package gg.fotia.tags.storage;

import gg.fotia.basictool.bridge.storage.SourceEntitlements;
import java.sql.*;
import java.util.*;

/** 身份只解锁称号；授权移除时仅清理已经没有任何有效来源的当前称号。 */
final class VipTitleSelection {
    private VipTitleSelection(){}
    static void apply(Connection c,SourceEntitlements grants,UUID player,String source,String tag,
                      long expires,long version,boolean revoke)throws SQLException {
        boolean sqlite=c.getMetaData().getDatabaseProductName().equalsIgnoreCase("SQLite");
        update(c,(sqlite?"INSERT OR IGNORE":"INSERT")+" INTO fotiatags_players(uuid) VALUES(?)"
                +(sqlite?"":" ON DUPLICATE KEY UPDATE uuid=uuid"),player.toString());
        String selected=null;
        try(var q=prepare(c,"SELECT current_tag FROM fotiatags_players WHERE uuid=?"+(sqlite?"":" FOR UPDATE"),player.toString());var r=q.executeQuery()){
            if(r.next())selected=r.getString(1);
        }
        boolean managed=false;
        if(selected!=null)try(var q=prepare(c,"SELECT source_id FROM fotiatags_level_rewards WHERE player_id=? AND resource_id=?",player.toString(),selected);var r=q.executeQuery()){
            while(r.next())if(r.getString(1).startsWith("basictool:vip:")||r.getString(1).startsWith("basictool:identity:"))managed=true;
        }
        if(!grants.apply(c,player,source,tag,expires,version,revoke))return;
        if(selected==null||!managed||grants.active(c,player).containsKey(selected))return;
        try(var q=prepare(c,"SELECT 1 FROM fotiatags_owned WHERE uuid=? AND tag_id=? AND (expire_time=-1 OR expire_time>?)",
                player.toString(),selected,System.currentTimeMillis());var r=q.executeQuery()){if(r.next())return;}
        update(c,"UPDATE fotiatags_players SET current_tag=NULL WHERE uuid=?",player.toString());
    }
    private static PreparedStatement prepare(Connection c,String query,Object...values)throws SQLException{
        var q=c.prepareStatement(query);q.setQueryTimeout(5);for(int i=0;i<values.length;i++)q.setObject(i+1,values[i]);return q;
    }
    private static void update(Connection c,String query,Object...values)throws SQLException{try(var q=prepare(c,query,values)){q.executeUpdate();}}
}

package gg.fotia.tags.storage;

import gg.fotia.basictool.bridge.storage.SourceEntitlements;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VipTitleSelectionTest {
    private Connection c;private SourceEntitlements grants;private final UUID player=UUID.randomUUID();
    private final String vip="basictool:vip:network:title-always",special="basictool:identity:network:streamer:title-p100-always";
    @BeforeEach void open()throws Exception{
        c=DriverManager.getConnection("jdbc:sqlite::memory:");
        try(var q=c.createStatement()){
            q.execute("CREATE TABLE fotiatags_players(uuid TEXT PRIMARY KEY,current_tag TEXT)");
            q.execute("CREATE TABLE fotiatags_owned(uuid TEXT,tag_id TEXT,expire_time BIGINT)");
        }
        grants=new SourceEntitlements("fotiatags_level_rewards");grants.initialize(c);c.setAutoCommit(false);
    }
    @AfterEach void close()throws Exception{c.close();}
    private void apply(String source,String tag,long version,boolean revoke)throws Exception{
        VipTitleSelection.apply(c,grants,player,source,tag,0,version,revoke);c.commit();
    }
    private String current()throws Exception{
        try(var q=c.prepareStatement("SELECT current_tag FROM fotiatags_players WHERE uuid=?")){
            q.setString(1,player.toString());try(var r=q.executeQuery()){r.next();return r.getString(1);}
        }
    }
    private void select(String tag)throws Exception{try(var q=c.prepareStatement("UPDATE fotiatags_players SET current_tag=?")){q.setString(1,tag);q.executeUpdate();}}
    @Test void grantsNeverAutoEquipEvenLegacyAlwaysSources()throws Exception{
        apply(vip,"vip",1,false);assertNull(current());select("custom");
        apply(special,"streamer",1,false);assertEquals("custom",current());
        select("vip");apply(special,"streamer",2,false);assertEquals("vip",current());
    }
    @Test void anotherSourceAndIndependentPurchaseSurviveRevocation()throws Exception{
        apply(vip,"shared",1,false);apply(special,"shared",1,false);select("shared");
        apply(special,"shared",2,true);assertEquals("shared",current());
        try(var q=c.prepareStatement("INSERT INTO fotiatags_owned VALUES(?,?,-1)")){
            q.setString(1,player.toString());q.setString(2,"shared");q.executeUpdate();
        }
        apply(vip,"shared",2,true);assertEquals("shared",current());
    }
    @Test void lastSourceRevocationUnequipsAndOldDeliveryCannotRestore()throws Exception{
        apply(special,"streamer",1,false);select("streamer");
        apply(special,"streamer",2,true);assertNull(current());
        apply(special,"streamer",1,false);assertNull(current());assertTrue(grants.active(c,player).isEmpty());
    }
}

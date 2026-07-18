package gg.fotia.tags.hook;

import gg.fotia.tags.FotiaTags;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class PaymentManager {

    private final FotiaTags plugin;
    private Economy economy;
    private PlayerPointsAPI playerPointsApi;

    public PaymentManager(FotiaTags plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.economy = null;
        this.playerPointsApi = null;

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                this.economy = registration.getProvider();
            }
        }

        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") instanceof PlayerPoints playerPoints) {
            this.playerPointsApi = playerPoints.getAPI();
        }
    }

    public String getProvider() {
        return plugin.getConfigManager().getConfig().getString("custom-tags.purchase.provider", "vault").toLowerCase();
    }

    public boolean isAvailable() {
        return isAvailable(createSnapshot());
    }

    public boolean isAvailable(PaymentSnapshot snapshot) {
        return switch (snapshot.provider()) {
            case "playerpoints" -> playerPointsApi != null;
            case "vault" -> economy != null;
            default -> false;
        };
    }

    public String getPriceText() {
        return getPriceText(createSnapshot());
    }

    public String getPriceText(PaymentSnapshot snapshot) {
        return switch (snapshot.provider()) {
            case "playerpoints" -> (int) Math.floor(snapshot.amount()) + " PlayerPoints";
            case "vault" -> snapshot.amount() + " 金币";
            default -> snapshot.provider();
        };
    }

    public boolean hasEnough(Player player) {
        return hasEnough(player, createSnapshot());
    }

    public boolean hasEnough(Player player, PaymentSnapshot snapshot) {
        return switch (snapshot.provider()) {
            case "playerpoints" -> playerPointsApi != null
                    && playerPointsApi.look(player.getUniqueId()) >= (int) Math.floor(snapshot.amount());
            case "vault" -> economy != null
                    && economy.has(player, snapshot.amount());
            default -> false;
        };
    }

    public boolean withdraw(Player player) {
        return withdraw(player, createSnapshot());
    }

    public boolean withdraw(Player player, PaymentSnapshot snapshot) {
        return switch (snapshot.provider()) {
            case "playerpoints" -> playerPointsApi != null
                    && playerPointsApi.take(player.getUniqueId(), (int) Math.floor(snapshot.amount()));
            case "vault" -> economy != null
                    && economy.withdrawPlayer(player, snapshot.amount()).transactionSuccess();
            default -> false;
        };
    }

    public PaymentSnapshot createSnapshot() {
        return switch (getProvider()) {
            case "playerpoints" -> new PaymentSnapshot("playerpoints", getPlayerPointsPrice());
            case "vault" -> new PaymentSnapshot("vault", getVaultPrice());
            default -> new PaymentSnapshot(getProvider(), 0.0);
        };
    }

    public boolean refund(Player player, String provider, double amount) {
        if (amount <= 0) {
            return true;
        }
        String normalized = provider != null ? provider.toLowerCase() : "";
        return switch (normalized) {
            case "playerpoints" -> {
                if (playerPointsApi == null) {
                    yield false;
                }
                int points = (int) Math.floor(amount);
                yield points <= 0 || playerPointsApi.give(player.getUniqueId(), points);
            }
            case "vault" -> economy != null && economy.depositPlayer(player, amount).transactionSuccess();
            default -> false;
        };
    }

    public double getVaultPrice() {
        return Math.max(0.0, plugin.getConfigManager().getConfig().getDouble("custom-tags.purchase.vault-price", 10000.0));
    }

    public int getPlayerPointsPrice() {
        return Math.max(0, plugin.getConfigManager().getConfig().getInt("custom-tags.purchase.playerpoints-price", 500));
    }

    public record PaymentSnapshot(String provider, double amount) {
    }
}

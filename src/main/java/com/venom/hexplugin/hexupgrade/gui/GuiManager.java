package com.venom.hexplugin.hexupgrade.gui;

import com.venom.hexplugin.hexupgrade.HexVGNetheriteUpgrade;
import com.venom.hexplugin.hexupgrade.economy.VaultHook;
import com.venom.hexplugin.hexupgrade.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {

    private final HexVGNetheriteUpgrade plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private final String titleRaw;
    private final int rows;
    private final int size;
    private final int upgradeSlot;
    private final int closeSlot;
    private final boolean fillers;
    private final Material fillerMat;

    private final ConcurrentHashMap<UUID, Long> busy = new ConcurrentHashMap<>();

    public GuiManager(HexVGNetheriteUpgrade plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        this.titleRaw = cfg.getString("gui.title", "&8Ulepszanie do Netherite");
        this.rows = Math.max(1, Math.min(6, cfg.getInt("gui.rows", 3)));
        this.size = rows * 9;
        this.upgradeSlot = clampSlot(cfg.getInt("gui.upgrade_slot", 13));
        this.closeSlot = clampSlot(cfg.getInt("gui.close_slot", 26));
        this.fillers = cfg.getBoolean("gui.show_fillers", true);
        this.fillerMat = Material.matchMaterial(cfg.getString("gui.filler_material", "GRAY_STAINED_GLASS_PANE"));
    }

    private int clampSlot(int raw) {
        return Math.max(0, Math.min(size - 1, raw));
    }

    private Component c(String s) {
        return legacy.deserialize(s == null ? "" : s);
    }

    private String msg(String path, String def) {
        String v = plugin.getMessages().getString(path);
        return v != null ? v : def;
    }

    // --- otwieranie menu po kliknięciu w smithing table ---
    @EventHandler
    public void onSmithingOpen(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock().getType() != Material.SMITHING_TABLE) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("hexvg.netheriteupgrade.use")) return;

        e.setCancelled(true);
        openMenu(p);
    }

    private static final class UpgradeMenu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        void setInventory(Inventory inv) { this.inv = inv; }
    }

    public void openMenu(Player p) {
        UpgradeMenu holder = new UpgradeMenu();
        Inventory inv = Bukkit.createInventory(holder, size, c(titleRaw));
        holder.setInventory(inv);

        if (fillers && fillerMat != null) {
            ItemStack fill = new ItemStack(fillerMat);
            ItemMeta fm = fill.getItemMeta();
            if (fm != null) {
                fm.displayName(Component.text(" "));
                fm.addItemFlags(ItemFlag.values());
                fill.setItemMeta(fm);
            }
            for (int i = 0; i < size; i++) inv.setItem(i, fill);
        }

        ItemStack upgrade = new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemMeta um = upgrade.getItemMeta();
        if (um != null) {
            um.displayName(legacy.deserialize(msg("upgrade_button", "&aUlepsz do &8Netherite")));
            List<String> lore = plugin.getMessages().getStringList("upgrade_lore");
            if (lore != null && !lore.isEmpty()) {
                double cost = resolveCostForHeld(p);
                lore = lore.stream().map(s -> s.replace("%cost%", String.format("%.2f", cost))).toList();
                um.lore(lore.stream().map(legacy::deserialize).toList());
            }
            um.addItemFlags(ItemFlag.values());
            upgrade.setItemMeta(um);
        }
        inv.setItem(upgradeSlot, upgrade);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) {
            cm.displayName(legacy.deserialize(msg("close_button", "&cZamknij")));
            cm.addItemFlags(ItemFlag.values());
            close.setItemMeta(cm);
        }
        inv.setItem(closeSlot, close);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof UpgradeMenu)) return;

        e.setCancelled(true);

        if (!p.hasPermission("hexvg.netheriteupgrade.use")) {
            p.closeInventory();
            return;
        }

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        if (slot == closeSlot) {
            p.closeInventory();
            sendMsg(p, msg("clicked_close", "&7Zamknięto menu."));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
            return;
        }

        if (slot == upgradeSlot) {
            handleUpgradeClick(p);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
    }

    private void handleUpgradeClick(Player p) {
        long now = System.currentTimeMillis();
        Long last = busy.get(p.getUniqueId());
        if (last != null && (now - last) < 500) {
            return;
        }
        busy.put(p.getUniqueId(), now);

        p.closeInventory();

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!ItemUtil.isDiamondUpgradeable(hand)) {
            p.sendActionBar(legacy.deserialize(msg("actionbar_not_diamond",
                    "&cNie można ulepszyć tego przedmiotu. Trzymaj diamentowy przedmiot w ręce!")));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }
        if (hand.getAmount() != 1) {
            p.sendActionBar(legacy.deserialize("&cUlepszaj pojedyncze przedmioty (amount=1)."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        double cost = resolveCostForHeld(p);

        if (plugin.getConfig().getBoolean("economy.enabled", true)) {
            VaultHook hook = plugin.getVaultHook();
            if (hook == null || !hook.isAvailable()) {
                sendMsg(p, msg("no_economy", "&cBrak ekonomii (Vault). Skontaktuj się z administracją."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
                return;
            }
            EconomyResponse rsp = hook.getEconomy().withdrawPlayer(p, cost);
            if (rsp == null || !rsp.transactionSuccess()) {
                sendMsg(p, msg("not_enough_money", "&cNie masz wystarczająco pieniędzy. Koszt: &e%cost%")
                        .replace("%cost%", String.format("%.2f", cost)));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
                return;
            }
        }

        hand = p.getInventory().getItemInMainHand();
        if (!ItemUtil.isDiamondUpgradeable(hand) || hand.getAmount() != 1) {
            refundIfNeeded(p, cost);
            p.sendActionBar(legacy.deserialize(msg("actionbar_not_diamond",
                    "&cNie można ulepszyć tego przedmiotu. Trzymaj diamentowy przedmiot w ręce!")));
            return;
        }

        ItemStack upgraded = ItemUtil.upgradeToNetherite(hand, plugin.getConfig());
        if (upgraded == null) {
            refundIfNeeded(p, cost);
            p.sendActionBar(legacy.deserialize(msg("actionbar_not_diamond",
                    "&cNie można ulepszyć tego przedmiotu. Trzymaj diamentowy przedmiot w ręce!")));
            return;
        }

        p.getInventory().setItemInMainHand(upgraded);

        sendMsg(p, msg("upgraded", "&aUlepszono do Netherite! &7(-%cost%)")
                .replace("%cost%", String.format("%.2f", cost)));
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> busy.remove(p.getUniqueId()), 10L);
    }

    private void refundIfNeeded(Player p, double cost) {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) return;
        VaultHook hook = plugin.getVaultHook();
        if (hook != null && hook.isAvailable()) {
            hook.getEconomy().depositPlayer(p, cost);
        }
    }

    private void sendMsg(Player p, String colored) {
        if (colored == null || colored.isEmpty()) return;
        p.sendMessage(legacy.deserialize(colored));
    }

    private double resolveCostForHeld(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        double def = safeCost(plugin.getConfig().getDouble("economy.default_cost", 10000.0));
        if (hand == null || hand.getType() == Material.AIR) return def;

        if (plugin.getConfig().isConfigurationSection("economy.costs")) {
            Map<String, Object> map =
                    plugin.getConfig().getConfigurationSection("economy.costs").getValues(false);
            if (map != null && map.containsKey(hand.getType().name())) {
                return safeCost(plugin.getConfig().getDouble("economy.costs." + hand.getType().name(), def));
            }
        }
        return def;
    }

    private double safeCost(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0D;
        if (v < 0) return 0.0D;
        return Math.min(v, 1_000_000_000D);
    }
}

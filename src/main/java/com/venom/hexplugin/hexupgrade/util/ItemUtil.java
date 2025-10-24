package com.venom.hexplugin.hexupgrade.util;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemUtil {

    private ItemUtil() {}

    private static final Map<Material, Material> DIAMOND_TO_NETHERITE = new EnumMap<>(Material.class);

    static {
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_AXE, Material.NETHERITE_AXE);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_HOE, Material.NETHERITE_HOE);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_HELMET, Material.NETHERITE_HELMET);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS);
        DIAMOND_TO_NETHERITE.put(Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS);
    }

    public static boolean isDiamondUpgradeable(ItemStack stack) {
        return stack != null && DIAMOND_TO_NETHERITE.containsKey(stack.getType());
    }

    public static ItemStack upgradeToNetherite(ItemStack original, FileConfiguration cfg) {
        if (original == null || !isDiamondUpgradeable(original)) return null;

        Material target = DIAMOND_TO_NETHERITE.get(original.getType());
        ItemStack out = new ItemStack(target, original.getAmount());

        ItemMeta src = original.getItemMeta();
        ItemMeta dst = out.getItemMeta();
        if (src != null && dst != null) {

            if (src.hasDisplayName()) dst.displayName(src.displayName());
            if (src.hasLore()) dst.lore(src.lore());

            boolean allowUnsafe = cfg.getBoolean("settings.allow_unsafe_enchants", false);
            if (src.hasEnchants()) {
                for (Map.Entry<Enchantment, Integer> e : src.getEnchants().entrySet()) {
                    Enchantment ench = e.getKey();
                    int lvl = e.getValue();
                    int finalLvl = allowUnsafe ? lvl : Math.min(lvl, ench.getMaxLevel());
                    dst.addEnchant(ench, finalLvl, allowUnsafe);
                }
            }

            if (src.hasCustomModelData()) {
                dst.setCustomModelData(src.getCustomModelData());
            }

            dst.setUnbreakable(src.isUnbreakable());

            if (src.getAttributeModifiers() != null) {
                dst.setAttributeModifiers(src.getAttributeModifiers());
            }

            Set<ItemFlag> flags = src.getItemFlags();
            List<String> whitelist = cfg.getStringList("settings.copy_item_flags_whitelist");
            if (whitelist != null && !whitelist.isEmpty()) {
                for (String name : whitelist) {
                    try {
                        ItemFlag f = ItemFlag.valueOf(name);
                        if (flags.contains(f)) dst.addItemFlags(f);
                    } catch (IllegalArgumentException ignored) {}
                }
            } else {
                if (!flags.isEmpty()) dst.addItemFlags(flags.toArray(ItemFlag[]::new));
            }
            if (cfg.getBoolean("settings.hide_enchants", true)) {
                dst.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            if (cfg.getBoolean("settings.keep_damage", true) && src instanceof Damageable sDmg && dst instanceof Damageable dDmg) {
                dDmg.setDamage(sDmg.getDamage());
            }

            out.setItemMeta(dst);
        }

        return out;
    }
}

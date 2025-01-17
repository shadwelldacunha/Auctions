/*
 * Copyright (C) SainttX <http://sainttx.com>
 * Copyright (C) contributors
 *
 * This file is part of Auctions.
 *
 * Auctions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Auctions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Auctions.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sainttx.auctions.command.subcommand;

import com.sainttx.auctions.AuctionPlugin;
import com.sainttx.auctions.api.Auction;
import com.sainttx.auctions.api.AuctionManager;
import com.sainttx.auctions.api.AuctionType;
import com.sainttx.auctions.api.Auctions;
import com.sainttx.auctions.api.event.AuctionCreateEvent;
import com.sainttx.auctions.api.messages.MessageHandler;
import com.sainttx.auctions.api.reward.ItemReward;
import com.sainttx.auctions.api.reward.Reward;
import com.sainttx.auctions.command.AuctionSubCommand;
import com.sainttx.auctions.structure.module.AntiSnipeModule;
import com.sainttx.auctions.structure.module.AutoWinModule;
import com.sainttx.auctions.util.AuctionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles the /auction start command for the auction plugin
 */
public class StartCommand extends AuctionSubCommand {

    public StartCommand(AuctionPlugin plugin) {
        super(plugin, "auctions.command.start", "start", "s", "star");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        MessageHandler handler = plugin.getManager().getMessageHandler();

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can start auctions");
        } else if (args.length < 3) {
            handler.sendMessage(sender, plugin.getMessage("messages.error.startSyntax"));
        } else if (plugin.getManager().isAuctioningDisabled() && !sender.hasPermission("auctions.bypass.general.disabled")) {
            handler.sendMessage(sender, plugin.getMessage("messages.error.auctionsDisabled"));
        } else if (!sender.hasPermission("auctions.bypass.general.disabledworld")
                && plugin.isWorldDisabled(((Player) sender).getWorld())) {
            handler.sendMessage(sender, plugin.getMessage("messages.error.cantUsePluginInWorld"));
        } else if (!plugin.getConfig().getBoolean("auctionSettings.sealedAuctions.enabled", false)
                && cmd.getName().equalsIgnoreCase("sealedauction")) {
            handler.sendMessage(sender, plugin.getMessage("messages.error.sealedAuctionsDisabled"));
        } else {
            Player player = (Player) sender;
            double fee = plugin.getConfig().getDouble("auctionSettings.startFee", 0);

            if (handler.isIgnoring(player)) {
                handler.sendMessage(player, plugin.getMessage("messages.error.currentlyIgnoring")); // player is ignoring
            } else if (fee > plugin.getEconomy().getBalance(player)) {
                handler.sendMessage(player, plugin.getMessage("messages.error.insufficientBalance")); // not enough funds
            } else if (player.getGameMode() == GameMode.CREATIVE
                    && !plugin.getConfig().getBoolean("auctionSettings.canAuctionInCreative", false)
                    && !player.hasPermission("auctions.bypass.general.creative")) {
                handler.sendMessage(player, plugin.getMessage("messages.error.creativeNotAllowed"));
            } else {
                Auction.Builder builder;

                if (cmd.getName().equals("sealedauction")) {
                    builder = Auctions.getAuctionBuilder(plugin, AuctionType.SEALED);
                } else {
                    builder = Auctions.getAuctionBuilder(plugin, AuctionType.STANDARD);
                }

                int amount; // the amount of items to auction
                double price; // the starting cost
                int increment = -1;
                double autowin = -1;

                try {
                    if (args[1].equalsIgnoreCase("all")) {
                        amount = getNumSimilarItem(player, player.getItemInHand());
                    } else {
                        amount = Integer.parseInt(args[1]);
                    }
                    price = Double.parseDouble(args[2]);

                    if (args.length > 3) {
                        increment = Integer.parseInt(args[3]);

                        if (!plugin.getConfig().getBoolean("auctionSettings.incrementCanExceedStartPrice")
                                && increment > price) {
                            handler.sendMessage(sender, plugin.getMessage("messages.error.biddingIncrementExceedsStart"));
                            return true;
                        }
                    }
                    if (args.length > 4) {
                        autowin = Double.parseDouble(args[4]);

                        if (autowin < 0) {
                            handler.sendMessage(player, plugin.getMessage("messages.error.invalidNumberEntered")); // negative amount
                            return true;
                        } else if (!player.hasPermission("auctions.bypass.start.maxautowin")
                                && autowin > plugin.getConfig().getDouble("auctionSettings.maximumAutowinAmount", 1000000D)) {
                            handler.sendMessage(sender, plugin.getMessage("messages.error.autowinTooHigh"));
                            return true;
                        }
                    }
                } catch (NumberFormatException ex) {
                    handler.sendMessage(sender, plugin.getMessage("messages.error.invalidNumberEntered"));
                    return true;
                }

                if (amount <= 0) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.invalidNumberEntered")); // negative amount
                } else if (amount > 2304) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.notEnoughOfItem")); // not enough
                } else if (Double.isInfinite(price) || Double.isNaN(price) || Double.isInfinite(autowin) || Double.isNaN(autowin)) {
                    handler.sendMessage(sender, plugin.getMessage("messages.error.invalidNumberEntered")); // invalid number
                } else if (price < plugin.getConfig().getDouble("auctionSettings.minimumStartPrice", 0)) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.startPriceTooLow")); // starting price too low
                } else if (!player.hasPermission("auctions.bypass.start.maxprice")
                        && price > plugin.getConfig().getDouble("auctionSettings.maximumStartPrice", 99999)) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.startPriceTooHigh")); // starting price too high
                } else if (plugin.getManager().getQueue().size() >= plugin.getConfig().getInt("auctionSettings.auctionQueueLimit", 3)) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.auctionQueueFull")); // queue full
                } else if (increment != -1 && (increment < plugin.getConfig().getInt("auctionSettings.minimumBidIncrement", 10)
                        || increment > plugin.getConfig().getInt("auctionSettings.maximumBidIncrement", 9999))) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.invalidBidIncrement"));
                } else if (autowin != -1 && !plugin.getConfig().getBoolean("auctionSettings.canSpecifyAutowin", true)) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.autowinDisabled"));
                } else if (autowin != -1 && Double.compare(autowin, price) <= 0) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.autowinBelowStart"));
                } else if (plugin.getManager().hasActiveAuction(player)) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.alreadyHaveAuction"));
                } else if (plugin.getManager().hasAuctionInQueue(player)) {
                    handler.sendMessage(player, plugin.getMessage("messages.error.alreadyInAuctionQueue"));
                } else {
                    ItemStack hand = player.getItemInHand();

                    if (hand == null || hand.getType() == Material.AIR) {
                        handler.sendMessage(player, plugin.getMessage("messages.error.invalidItemType")); // auctioned nothing
                    } else if (!player.hasPermission("auctions.bypass.general.bannedmaterial")
                            && plugin.getManager().isBannedMaterial(hand.getType())) {
                        handler.sendMessage(player, plugin.getMessage("messages.error.invalidItemType")); // item type not allowed
                    } else if (!player.hasPermission("auctions.bypass.general.damageditems")
                            && hand.getType().getMaxDurability() > 0 && hand.getDurability() > 0
                            && !plugin.getConfig().getBoolean("auctionSettings.canAuctionDamagedItems", true)) {
                        handler.sendMessage(player, plugin.getMessage("messages.error.cantAuctionDamagedItems")); // can't auction damaged
                    } else if (AuctionUtil.getAmountItems(player.getInventory(), hand) < amount) {
                        handler.sendMessage(player, plugin.getMessage("messages.error.notEnoughOfItem"));
                    } else if (!player.hasPermission("auctions.bypass.general.nameditems")
                            && !plugin.getConfig().getBoolean("auctionSettings.canAuctionNamedItems", true)
                            && hand.getItemMeta().hasDisplayName()) {
                        handler.sendMessage(player, plugin.getMessage("messages.error.cantAuctionNamedItems")); // cant auction named
                    } else if (!player.hasPermission("auctions.bypass.general.bannedlore") && hasBannedLore(hand)) {
                        // The players item contains a piece of denied lore
                        handler.sendMessage(player, plugin.getMessage("messages.error.cantAuctionBannedLore"));
                    } else {
                        ItemStack item = new ItemStack(hand);
                        item.setAmount(amount);
                        Reward reward = new ItemReward(plugin, item);
                        builder.bidIncrement(increment)
                                .reward(reward)
                                .owner(player)
                                .topBid(price)
                                .autowin(autowin);
                        Auction created = builder.build();

                        // check if we can add an autowin module
                        if (created.getAutowin() > 0) {
                            created.addModule(new AutoWinModule(plugin, created, autowin));
                        }

                        // check if we can add an anti snipe module
                        if (plugin.getConfig().getBoolean("auctionSettings.antiSnipe.enable", true)) {
                            created.addModule(new AntiSnipeModule(plugin, created));
                        }

                        AuctionCreateEvent event = new AuctionCreateEvent(created, player);
                        Bukkit.getPluginManager().callEvent(event);

                        if (event.isCancelled()) {
                            return true;
                        }

                        player.getInventory().removeItem(item); // take the item from the player
                        plugin.getEconomy().withdrawPlayer(player, fee); // withdraw the start fee

                        if (plugin.getManager().canStartNewAuction()) {
                            plugin.getManager().setCurrentAuction(created);
                            created.start();
                            plugin.getManager().setCanStartNewAuction(false);
                        } else {
                            plugin.getManager().addAuctionToQueue(created);
                            handler.sendMessage(player, plugin.getMessage("messages.auctionPlacedInQueue"));
                        }
                    }
                }
            }
        }

        return false;
    }

    /*
     * Gets the total amount of an item that a player is holding
     */
    private int getNumSimilarItem(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        int amount = 0;

        if (item == null || item.getType() == Material.AIR) {
            return 1;
        }

        for (ItemStack itm : inv) {
            if (itm != null && itm.isSimilar(item)) {
                amount += itm.getAmount();
            }
        }

        return amount;
    }

    /**
     * Checks if an item has a banned piece of lore
     *
     * @param item the item
     * @return true if the item has a banned piece of lore
     */
    public boolean hasBannedLore(ItemStack item) {
        List<String> bannedLore = plugin.getConfig().getStringList("general.blockedLore");

        if (bannedLore != null && !bannedLore.isEmpty()) {
            if (item.getItemMeta().hasLore()) {
                List<String> lore = item.getItemMeta().getLore();

                for (String loreItem : lore) {
                    for (String banned : bannedLore) {
                        if (loreItem.contains(ChatColor.translateAlternateColorCodes('&', banned))) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}

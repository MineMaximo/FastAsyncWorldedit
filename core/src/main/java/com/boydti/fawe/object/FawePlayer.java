package com.boydti.fawe.object;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.BBC;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.changeset.DiskStorageHistory;
import com.boydti.fawe.util.WEManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.world.World;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class FawePlayer<T> {

    public final T parent;
    private LocalSession session;

    /**
     * The metadata map.
     */
    private volatile ConcurrentHashMap<String, Object> meta;

    public static <V> FawePlayer<V> wrap(final Object obj) {
        return Fawe.imp().wrap(obj);
    }

    public FawePlayer(final T parent) {
        this.parent = parent;
        Fawe.get().register(this);
        if (getSession() == null || getPlayer() == null || session.getSize() != 0 || !Settings.STORE_HISTORY_ON_DISK) {
            return;
        }
        try {
            UUID uuid = getUUID();
            String currentWorldName = getLocation().world;
            World world = getWorld();
            if (world != null) {
                if (world.getName().equals(currentWorldName)) {
                    getSession().clearHistory();
                    loadSessionFromDisk(world);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Fawe.debug("Failed to load history for: " + getName());
        }
    }

    public World getWorld() {
        String currentWorldName = getLocation().world;
        for (World world : WorldEdit.getInstance().getServer().getWorlds()) {
            if (world.getName().equals(currentWorldName)) {
                return world;
            }
        }
        return null;
    }

    public void loadSessionFromDisk(World world) {
        if (world == null) {
            return;
        }
        UUID uuid = getUUID();
        List<Integer> editIds = new ArrayList<>();
        File folder = new File(Fawe.imp().getDirectory(), "history" + File.separator + world.getName() + File.separator + uuid);
        if (folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                if (file.getName().endsWith(".bd")) {
                    int index = Integer.parseInt(file.getName().split("\\.")[0]);
                    editIds.add(index);
                }
            }
        }
        Collections.sort(editIds);
        if (editIds.size() > 0) {
            Fawe.debug(BBC.PREFIX.s() + " Indexing " + editIds.size() + " history objects for " + getName());
            for (int index : editIds) {
                DiskStorageHistory set = new DiskStorageHistory(world, uuid, index);
                EditSession edit = set.toEditSession(getPlayer());
                session.remember(edit);
            }
        }
    }

    public FaweLimit getLimit() {
        return Settings.getLimit(this);
    }

    public abstract String getName();

    public abstract UUID getUUID();

    public abstract boolean hasPermission(final String perm);

    public abstract void setPermission(final String perm, final boolean flag);

    public abstract void sendMessage(final String message);

    public abstract void executeCommand(final String substring);

    public abstract FaweLocation getLocation();

    public abstract Player getPlayer();

    public Region getSelection() {
        try {
            return this.getSession().getSelection(this.getPlayer().getWorld());
        } catch (final IncompleteRegionException e) {
            return null;
        }
    }

    public LocalSession getSession() {
        return (this.session != null || this.getPlayer() == null) ? this.session : (session = Fawe.get().getWorldEdit().getSession(this.getPlayer()));
    }

    public HashSet<RegionWrapper> getCurrentRegions() {
        return WEManager.IMP.getMask(this);
    }

    public void setSelection(final RegionWrapper region) {
        final Player player = this.getPlayer();
        final RegionSelector selector = new CuboidRegionSelector(player.getWorld(), region.getBottomVector(), region.getTopVector());
        this.getSession().setRegionSelector(player.getWorld(), selector);
    }

    public RegionWrapper getLargestRegion() {
        int area = 0;
        RegionWrapper max = null;
        for (final RegionWrapper region : this.getCurrentRegions()) {
            final int tmp = (region.maxX - region.minX) * (region.maxZ - region.minZ);
            if (tmp > area) {
                area = tmp;
                max = region;
            }
        }
        return max;
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public boolean hasWorldEditBypass() {
        return this.hasPermission("fawe.bypass");
    }

    /**
     * Set some session only metadata for the player
     * @param key
     * @param value
     */
    public void setMeta(String key, Object value) {
        if (this.meta == null) {
            this.meta = new ConcurrentHashMap<>();
        }
        this.meta.put(key, value);
    }

    /**
     * Get the metadata for a key.
     * @param <V>
     * @param key
     * @return
     */
    public <V> V getMeta(String key) {
        if (this.meta != null) {
            return (V) this.meta.get(key);
        }
        return null;
    }

    public <V> V getMeta(String key, V def) {
        if (this.meta != null) {
            V value = (V) this.meta.get(key);
            return value == null ? def : value;
        }
        return def;
    }

    /**
     * Delete the metadata for a key.
     *  - metadata is session only
     *  - deleting other plugin's metadata may cause issues
     * @param key
     */
    public Object deleteMeta(String key) {
        return this.meta == null ? null : this.meta.remove(key);
    }

    public void unregister() {
        getSession().setClipboard(null);
        getSession().clearHistory();
        WorldEdit.getInstance().removeSession(getPlayer());
        Fawe.get().unregister(getName());
    }
}

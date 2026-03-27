package com.scriptica.lang;

import java.util.List;

public interface ScripticaHost {
    void log(String message);

    void chat(String message);

    void command(String command);

    void sleepTicks(long ticks, CancellationToken token) throws InterruptedException;

    // --- Modules ---

    /**
     * Loads a Scriptica script by name (for example: "util" for util.sca).
     * Return null if not found.
     */
    String loadScript(String name);

    // --- Events ---

    /** Registers a tick callback (fn arity must be 0). Returns a handle id. */
    double onTick(ScripticaCallable fn, CancellationToken token);

    /** Registers a chat callback (fn arity must be 1). Returns a handle id. */
    double onChat(ScripticaCallable fn, CancellationToken token);


    /** Registers a custom event callback (fn arity must be 1: payload). Returns a handle id. */
    double on(String name, ScripticaCallable fn, CancellationToken token);

    /** Emits a custom event to listeners registered via on(name, fn). */
    void emit(String name, Value payload);    /** Unregisters an event handle returned by onTick/onChat. */
    void off(double handle);

    // --- Automation / player control (client-side) ---

    double playerX();

    double playerY();

    double playerZ();

    double playerYaw();

    double playerPitch();

    void look(double yaw, double pitch);

    void turn(double yawDelta, double pitchDelta);

    void setKey(String key, boolean down);

    void releaseAllKeys();

    void attack();

    void use();

    void hotbar(int slot1to9);

    // --- Player state ---

    double health();

    double hunger();

    double armor();

    boolean onGround();

    boolean sneaking();

    boolean sprinting();

    String dimension();

    String heldItem();

    String offhandItem();

    // --- World / inventory helpers ---

    /**
     * Returns a map describing what the player is pointing at.
     */
    Object raycast(double maxDistance);

    /**
     * Returns the block id at the position (e.g. "minecraft:stone").
     */
    String blockAt(int x, int y, int z);

    /**
     * Returns a list of entity maps within radius.
     * typeFilter may be null/empty to return everything.
     */
    List<Object> entities(double radius, String typeFilter);

    /**
     * Counts items in the player's inventory.
     */
    double invCount(String itemId);

    /**
     * Selects a hotbar slot containing the item id. Returns true if found.
     */
    boolean invSelect(String itemId);

    /**
     * Returns a map describing an inventory slot (id/count) or null.
     */
    Object invSlot(int slot);

    /**
     * Returns a list of inventory slot indexes containing the item id.
     */
    List<Object> invFind(String itemId);

    /**
     * Selects a hotbar slot 1..9.
     */
    boolean invSelectSlot(int slot1to9);

    /**
     * Attempts to left-click (break) a block position.
     */
    boolean attackBlock(int x, int y, int z, String side);

    /**
     * Attempts to right-click a block position with main hand.
     */
    boolean useOnBlock(int x, int y, int z, String side);

    /** Finds block positions around the player. */
    List<Object> findBlocks(String blockId, int radius, int maxResults);

    /** Returns the nearest block position map or null. */
    Object nearestBlock(String blockId, int radius);

    /** Returns an entity map by id or null. */
    Object entity(int entityId);

    /** Attempts to attack (left click) an entity by id. */
    boolean attackEntity(int entityId);

    /** Attempts to use (right click) an entity by id with main hand. */
    boolean useEntity(int entityId);
}



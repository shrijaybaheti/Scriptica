package com.scriptica.client;

import com.scriptica.lang.CancellationToken;
import com.scriptica.lang.ScripticaCallable;
import com.scriptica.lang.ScripticaHost;
import com.scriptica.lang.Value;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MinecraftScripticaHost implements ScripticaHost {
    @Override
    public void log(String message) {
        String msg = message == null ? "null" : message;
        ScripticaLog.append("[script] " + msg);
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            client.player.sendMessage(Text.literal("[Scriptica] " + msg), false);
        });
    }

    @Override
    public void chat(String message) {
        String msg = message == null ? "" : message;
        ScripticaLog.append("[chat] " + msg);
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            client.player.networkHandler.sendChatMessage(msg);
        });
    }

    @Override
    public void command(String command) {
        String cmd = command == null ? "" : command.trim();
        if (!cmd.startsWith("/")) cmd = "/" + cmd;
        String finalCmd = cmd;
        ScripticaLog.append("[cmd] " + finalCmd);
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            String cmdOnly = finalCmd.startsWith("/") ? finalCmd.substring(1) : finalCmd;
            client.player.networkHandler.sendChatCommand(cmdOnly);
        });
    }

    @Override
    public void sleepTicks(long ticks, CancellationToken token) throws InterruptedException {
        long remaining = Math.max(0, ticks);
        while (remaining > 0) {
            token.checkCancelled();
            long chunk = Math.min(remaining, 5);
            Thread.sleep(chunk * 50L);
            remaining -= chunk;
        }
    }

    @Override
    public String loadScript(String name) {
        String n = ScriptStorage.sanitizeName(name == null ? "" : name);
        try {
            return ScriptStorage.load(n);
        } catch (Exception e) {
            return null;
        }
    }


    // --- Events ---

    @Override
    public double onTick(ScripticaCallable fn, CancellationToken token) {
        return ScriptRunner.instance().registerTick(fn, token);
    }

    @Override
    public double onChat(ScripticaCallable fn, CancellationToken token) {
        return ScriptRunner.instance().registerChat(fn, token);
    }

    @Override
    public void off(double handle) {
        ScriptRunner.instance().unregisterEvent(handle);
    }

    @Override
    public double on(String name, ScripticaCallable fn, CancellationToken token) {
        return ScriptRunner.instance().registerCustom(name, fn, token);
    }

    @Override
    public void emit(String name, Value payload) {
        ScriptRunner.instance().dispatchCustom(name, payload);
    }

    @Override
    public double playerX() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return client.player.getX();
        }, 0.0);
    }

    @Override
    public double playerY() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return client.player.getY();
        }, 0.0);
    }

    @Override
    public double playerZ() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return client.player.getZ();
        }, 0.0);
    }

    @Override
    public double playerYaw() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return (double) client.player.getYaw();
        }, 0.0);
    }

    @Override
    public double playerPitch() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return (double) client.player.getPitch();
        }, 0.0);
    }

    @Override
    public void look(double yaw, double pitch) {
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            client.player.setYaw((float) yaw);
            client.player.setPitch((float) pitch);
        });
    }

    @Override
    public void turn(double yawDelta, double pitchDelta) {
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            float yaw = client.player.getYaw();
            float pitch = client.player.getPitch();
            client.player.setYaw(yaw + (float) yawDelta);
            client.player.setPitch(pitch + (float) pitchDelta);
        });
    }

    @Override
    public void setKey(String key, boolean down) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.options == null) return;

            switch (k) {
                case "w", "forward" -> client.options.forwardKey.setPressed(down);
                case "s", "back", "backward" -> client.options.backKey.setPressed(down);
                case "a", "left" -> client.options.leftKey.setPressed(down);
                case "d", "right" -> client.options.rightKey.setPressed(down);
                case "jump", "space" -> client.options.jumpKey.setPressed(down);
                case "sneak", "shift" -> client.options.sneakKey.setPressed(down);
                case "sprint", "ctrl", "control" -> client.options.sprintKey.setPressed(down);
                case "attack", "lmb", "leftclick" -> client.options.attackKey.setPressed(down);
                case "use", "rmb", "rightclick" -> client.options.useKey.setPressed(down);
                default -> {
                    // ignore unknown key names
                }
            }
        });
    }

    @Override
    public void releaseAllKeys() {
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.options == null) return;
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            client.options.attackKey.setPressed(false);
            client.options.useKey.setPressed(false);
        });
    }

    @Override
    public void attack() {
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;

            if (tryInvokePrivate(client, "doAttack")) return;

            if (client.options != null) {
                client.options.attackKey.setPressed(true);
                tryInvokePrivate(client, "handleInputEvents");
                client.options.attackKey.setPressed(false);
            }
        });
    }

    @Override
    public void use() {
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;

            if (tryInvokePrivate(client, "doItemUse")) return;

            if (client.options != null) {
                client.options.useKey.setPressed(true);
                tryInvokePrivate(client, "handleInputEvents");
                client.options.useKey.setPressed(false);
            }
        });
    }

    @Override
    public void hotbar(int slot1to9) {
        int s = Math.max(1, Math.min(9, slot1to9));
        int idx = s - 1;
        MinecraftThread.runSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            Object inv = client.player.getInventory();

            try {
                Method m = inv.getClass().getMethod("setSelectedSlot", int.class);
                m.invoke(inv, idx);
                return;
            } catch (Exception ignored) {
            }

            try {
                Field f = inv.getClass().getDeclaredField("selectedSlot");
                f.setAccessible(true);
                f.setInt(inv, idx);
            } catch (Exception ignored) {
            }
        });
    }
    @Override
    public double health() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return (double) client.player.getHealth();
        }, 0.0);
    }

    @Override
    public double hunger() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return (double) client.player.getHungerManager().getFoodLevel();
        }, 0.0);
    }

    @Override
    public double armor() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            return (double) client.player.getArmor();
        }, 0.0);
    }

    @Override
    public boolean onGround() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return false;
            return client.player.isOnGround();
        }, false);
    }

    @Override
    public boolean sneaking() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return false;
            return client.player.isSneaking();
        }, false);
    }

    @Override
    public boolean sprinting() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return false;
            return client.player.isSprinting();
        }, false);
    }

    @Override
    public String dimension() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return "";
            return client.world.getRegistryKey().getValue().toString();
        }, "");
    }

    @Override
    public String heldItem() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return "minecraft:air";
            ItemStack st = client.player.getMainHandStack();
            if (st == null || st.isEmpty()) return "minecraft:air";
            return Registries.ITEM.getId(st.getItem()).toString();
        }, "minecraft:air");
    }

    @Override
    public String offhandItem() {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return "minecraft:air";
            ItemStack st = client.player.getOffHandStack();
            if (st == null || st.isEmpty()) return "minecraft:air";
            return Registries.ITEM.getId(st.getItem()).toString();
        }, "minecraft:air");
    }

    @Override
    public Object raycast(double maxDistance) {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return null;
            HitResult hit = client.player.raycast(maxDistance, 0.0f, false);
            Map<String, Object> out = new HashMap<>();
            out.put("type", hit.getType().name().toLowerCase(Locale.ROOT));
            out.put("x", hit.getPos().x);
            out.put("y", hit.getPos().y);
            out.put("z", hit.getPos().z);

            if (hit instanceof BlockHitResult bhr) {
                BlockPos bp = bhr.getBlockPos();
                out.put("blockPos", bp.getX() + "," + bp.getY() + "," + bp.getZ());
                out.put("side", bhr.getSide().asString());
                String id = Registries.BLOCK.getId(client.world.getBlockState(bp).getBlock()).toString();
                out.put("block", id);
            } else if (hit instanceof EntityHitResult ehr) {
                Entity e = ehr.getEntity();
                out.put("entityId", e.getId());
                out.put("entityType", Registries.ENTITY_TYPE.getId(e.getType()).toString());
                out.put("name", e.getName().getString());
            }

            return out;
        }, null);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return null;
            BlockPos pos = new BlockPos(x, y, z);
            return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
        }, null);
    }

    @Override
    public List<Object> entities(double radius, String typeFilter) {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null) return List.of();
            double r = Math.max(0.0, radius);
            String filter = typeFilter == null ? "" : typeFilter.trim().toLowerCase(Locale.ROOT);

            Box box = client.player.getBoundingBox().expand(r);
            List<Entity> ents = client.world.getOtherEntities(client.player, box, e -> true);
            List<Object> out = new ArrayList<>();
            for (Entity e : ents) {
                String type = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                if (!filter.isEmpty() && !type.toLowerCase(Locale.ROOT).contains(filter)) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("id", e.getId());
                m.put("type", type);
                m.put("name", e.getName().getString());
                m.put("x", e.getX());
                m.put("y", e.getY());
                m.put("z", e.getZ());
                m.put("distance", client.player.distanceTo(e));
                out.add(m);
            }
            return out;
        }, List.of());
    }

    @Override
    public double invCount(String itemId) {
        String id = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return 0.0;
            int total = 0;
            var inv = client.player.getInventory();
            int size;
            try {
                Method m = inv.getClass().getMethod("size");
                size = (int) m.invoke(inv);
            } catch (Exception ignored) {
                size = 36;
            }
            for (int i = 0; i < size; i++) {
                ItemStack st;
                try {
                    Method m = inv.getClass().getMethod("getStack", int.class);
                    st = (ItemStack) m.invoke(inv, i);
                } catch (Exception e) {
                    continue;
                }
                if (st == null || st.isEmpty()) continue;
                String sid = Registries.ITEM.getId(st.getItem()).toString();
                if (id.isEmpty() || sid.equalsIgnoreCase(id)) {
                    total += st.getCount();
                }
            }
            return (double) total;
        }, 0.0);
    }

    @Override
    public boolean invSelect(String itemId) {
        String id = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return false;
            var inv = client.player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack st;
                try {
                    Method m = inv.getClass().getMethod("getStack", int.class);
                    st = (ItemStack) m.invoke(inv, i);
                } catch (Exception e) {
                    continue;
                }
                if (st == null || st.isEmpty()) continue;
                String sid = Registries.ITEM.getId(st.getItem()).toString();
                if (sid.equalsIgnoreCase(id)) {
                    hotbar(i + 1);
                    return true;
                }
            }
            return false;
        }, false);
    }

    @Override
    public Object invSlot(int slot) {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return null;
            var inv = client.player.getInventory();
            ItemStack st;
            try {
                Method m = inv.getClass().getMethod("getStack", int.class);
                st = (ItemStack) m.invoke(inv, slot);
            } catch (Exception e) {
                return null;
            }
            if (st == null || st.isEmpty()) return null;
            Map<String, Object> out = new HashMap<>();
            out.put("id", Registries.ITEM.getId(st.getItem()).toString());
            out.put("count", (double) st.getCount());
            return out;
        }, null);
    }


    @Override
    public List<Object> invFind(String itemId) {
        String id = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return List.of();
            var inv = client.player.getInventory();

            int size;
            try {
                Method m = inv.getClass().getMethod("size");
                size = (int) m.invoke(inv);
            } catch (Exception ignored) {
                size = 36;
            }

            List<Object> out = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ItemStack st;
                try {
                    Method m = inv.getClass().getMethod("getStack", int.class);
                    st = (ItemStack) m.invoke(inv, i);
                } catch (Exception e) {
                    continue;
                }
                if (st == null || st.isEmpty()) continue;
                String sid = Registries.ITEM.getId(st.getItem()).toString();
                if (id.isEmpty() || sid.equalsIgnoreCase(id)) {
                    out.add((double) i);
                }
            }
            return out;
        }, List.of());
    }

    private static boolean tryInvokePrivate(MinecraftClient client, String methodName) {
        try {
            Method m = MinecraftClient.class.getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(client);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean invSelectSlot(int slot1to9) {
        int s = Math.max(1, Math.min(9, slot1to9));
        hotbar(s);
        return true;
    }

    @Override
    public boolean attackBlock(int x, int y, int z, String side) {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.interactionManager == null) return false;
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, y, z);
            net.minecraft.util.math.Direction dir = parseSide(side);

            Object mgr = client.interactionManager;
            for (Method m : mgr.getClass().getMethods()) {
                if (!m.getName().toLowerCase(Locale.ROOT).contains("attack")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0].getName().endsWith("BlockPos") && p[1].getName().endsWith("Direction")) {
                    try {
                        Object r = m.invoke(mgr, pos, dir);
                        if (r instanceof Boolean b) return b;
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
            return false;
        }, false);
    }

    @Override
    public boolean useOnBlock(int x, int y, int z, String side) {
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.interactionManager == null) return false;
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, y, z);
            net.minecraft.util.math.Direction dir = parseSide(side);
            net.minecraft.util.math.Vec3d hitPos = net.minecraft.util.math.Vec3d.ofCenter(pos).add(
                (double) dir.getOffsetX() * 0.5,
                (double) dir.getOffsetY() * 0.5,
                (double) dir.getOffsetZ() * 0.5
            );
            BlockHitResult hit = new BlockHitResult(hitPos, dir, pos, false);

            Object mgr = client.interactionManager;
            for (Method m : mgr.getClass().getMethods()) {
                if (!m.getName().toLowerCase(Locale.ROOT).contains("interact") || !m.getName().toLowerCase(Locale.ROOT).contains("block")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[2].getName().endsWith("BlockHitResult")) {
                    try {
                        Object r = m.invoke(mgr, client.player, net.minecraft.util.Hand.MAIN_HAND, hit);
                        if (r == null) return true;
                        try {
                            Method ok = r.getClass().getMethod("isAccepted");
                            Object v = ok.invoke(r);
                            if (v instanceof Boolean b) return b;
                        } catch (Exception ignored) {
                        }
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
            return false;
        }, false);
    }


    @Override
    public List<Object> findBlocks(String blockId, int radius, int maxResults) {
        String id = blockId == null ? "" : blockId.trim().toLowerCase(Locale.ROOT);
        int r = Math.max(0, Math.min(64, radius));
        int max = Math.max(0, Math.min(2000, maxResults));

        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null) return List.of();
            BlockPos center = client.player.getBlockPos();

            List<Object> out = new ArrayList<>();
            if (max == 0) return out;

            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = center.add(dx, dy, dz);
                        String bid = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
                        if (!id.isEmpty() && !bid.equalsIgnoreCase(id)) continue;

                        Map<String, Object> m = new HashMap<>();
                        m.put("x", (double) pos.getX());
                        m.put("y", (double) pos.getY());
                        m.put("z", (double) pos.getZ());
                        m.put("id", bid);
                        out.add(m);

                        if (out.size() >= max) return out;
                    }
                }
            }
            return out;
        }, List.of());
    }

    @Override
    public Object nearestBlock(String blockId, int radius) {
        String id = blockId == null ? "" : blockId.trim().toLowerCase(Locale.ROOT);
        int r = Math.max(0, Math.min(128, radius));
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null) return null;
            if (id.isEmpty()) return null;

            BlockPos center = client.player.getBlockPos();
            double bestDist2 = Double.POSITIVE_INFINITY;
            BlockPos best = null;

            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = center.add(dx, dy, dz);
                        String bid = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
                        if (!bid.equalsIgnoreCase(id)) continue;

                        double d2 = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                        if (d2 < bestDist2) {
                            bestDist2 = d2;
                            best = pos;
                        }
                    }
                }
            }

            if (best == null) return null;
            Map<String, Object> m = new HashMap<>();
            m.put("x", (double) best.getX());
            m.put("y", (double) best.getY());
            m.put("z", (double) best.getZ());
            m.put("id", id);
            m.put("dist2", bestDist2);
            return m;
        }, null);
    }

    @Override
    public Object entity(int entityId) {
        int id = entityId;
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return null;
            Entity e = client.world.getEntityById(id);
            if (e == null) return null;
            Map<String, Object> out = new HashMap<>();
            out.put("id", (double) e.getId());
            out.put("type", Registries.ENTITY_TYPE.getId(e.getType()).toString());
            out.put("name", e.getName().getString());
            out.put("x", e.getX());
            out.put("y", e.getY());
            out.put("z", e.getZ());
            return out;
        }, null);
    }

    @Override
    public boolean attackEntity(int entityId) {
        int id = entityId;
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null || client.interactionManager == null) return false;
            Entity e = client.world.getEntityById(id);
            if (e == null) return false;

            Object mgr = client.interactionManager;
            for (Method m : mgr.getClass().getMethods()) {
                if (!m.getName().toLowerCase(Locale.ROOT).contains("attack") || !m.getName().toLowerCase(Locale.ROOT).contains("entity")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2) {
                    try {
                        m.invoke(mgr, client.player, e);
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
            return false;
        }, false);
    }

    @Override
    public boolean useEntity(int entityId) {
        int id = entityId;
        return MinecraftThread.callSync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null || client.interactionManager == null) return false;
            Entity e = client.world.getEntityById(id);
            if (e == null) return false;

            Object mgr = client.interactionManager;
            for (Method m : mgr.getClass().getMethods()) {
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("interact") || !n.contains("entity")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[2].getName().endsWith("Hand")) {
                    try {
                        Object r = m.invoke(mgr, client.player, e, net.minecraft.util.Hand.MAIN_HAND);
                        if (r == null) return true;
                        try {
                            Method ok = r.getClass().getMethod("isAccepted");
                            Object v = ok.invoke(r);
                            if (v instanceof Boolean b) return b;
                        } catch (Exception ignored) {
                        }
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
            return false;
        }, false);
    }

    private static net.minecraft.util.math.Direction parseSide(String side) {
        String s = side == null ? "up" : side.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "down", "bottom" -> net.minecraft.util.math.Direction.DOWN;
            case "north", "n" -> net.minecraft.util.math.Direction.NORTH;
            case "south", "s" -> net.minecraft.util.math.Direction.SOUTH;
            case "west", "w" -> net.minecraft.util.math.Direction.WEST;
            case "east", "e" -> net.minecraft.util.math.Direction.EAST;
            default -> net.minecraft.util.math.Direction.UP;
        };
    }
}


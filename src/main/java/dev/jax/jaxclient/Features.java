package dev.jax.jaxclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.List;

final class Features {
    private Features() {}

    static final List<Feature> ALL = new ArrayList<>();

    static Feature fullbright;
    static Feature autoSprint;
    static Feature fps;
    static Feature ping;
    static Feature clock;
    static Feature coords;
    static Feature keystrokes;
    static Feature keybindList;
    static Feature armorStatus;
    static Feature direction;
    static Feature biome;
    static Feature speedometer;
    static Feature worldTime;
    static Feature foodStatus;
    static Feature autoTool;
    static Feature armorAlert;
    static Feature suspiciousChunkFinder;

    static void init(Config cfg) {
        ALL.clear();

        fullbright = add(new Feature("Fullbright", "Maximum brightness in dark areas",
                Feature.Category.RENDER, false) {
            @Override
            void onTick(MinecraftClient mc) {
                StatusEffectInstance nv = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (nv == null || nv.getDuration() < 400) {
                    mc.player.addStatusEffect(
                            new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false));
                }
            }

            @Override
            void onDisable(MinecraftClient mc) {
                if (mc.player != null) {
                    mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                }
            }
        });

        autoSprint = add(new Feature("AutoSprint", "Keeps you sprinting while moving forward",
                Feature.Category.MOVEMENT, false) {
            @Override
            void onTick(MinecraftClient mc) {
                GameOptions o = mc.options;
                // Recompute every tick so releasing forward or pressing sneak cannot leave sprint latched.
                o.sprintKey.setPressed(o.forwardKey.isPressed() && !o.sneakKey.isPressed());
            }

            @Override
            void onDisable(MinecraftClient mc) {
                mc.options.sprintKey.setPressed(false);
            }
        });

        fps = hud("FPS Counter", "Frames per second, bottom-right");
        ping = hud("Ping", "Server latency, bottom-right");
        clock = hud("Clock", "Local time, bottom-right");
        coords = hud("Coordinates", "Your XYZ position, top-left");
        keystrokes = hud("Keystrokes", "WASD, mouse and space overlay");
        keybindList = hud("Keybind List", "Hotkeys for each module");
        armorStatus = hud("Armor Status", "Armor durability and equipped items");
        direction = hud("Direction", "Compass direction indicator");
        biome = hud("Biome", "Current biome name");
        speedometer = hud("Speedometer", "Horizontal movement speed");
        worldTime = hud("World Time", "In-game clock and day count");
        foodStatus = hud("Food Status", "Food level and saturation context");

        autoTool = add(new Feature("Auto Tool", "Switches to the fastest tool when mining starts", Feature.Category.UTILITY, true));

        suspiciousChunkFinder = add(new Feature("Suspicious Chunk Finder",
                "Prioritizes the closest underground light and redstone detections", Feature.Category.WORLD, true) {
            private int tick;
            private record Detection(
        int x,
        int z,
        int redstone,
        int powered,
        int artificialLight
) {}private int cursor;
            private final java.util.Map<Long, Detection> candidates = new java.util.HashMap<>();
            private final java.util.Set<Long> reported = new java.util.HashSet<>();

            @Override
            void onEnable(MinecraftClient mc) {
                cursor = 0;
                tick = 0;
                candidates.clear();
                reported.clear();
            }

            @Override
            void onTick(MinecraftClient mc) {
                if (mc.player == null || mc.world == null) return;
                tick++;
                int radius = 4;
                int diameter = radius * 2 + 1;
                int index = cursor++ % (diameter * diameter);
                int playerChunkX = mc.player.getChunkPos().x;
                int playerChunkZ = mc.player.getChunkPos().z;
                int cx = playerChunkX + (index % diameter) - radius;
                int cz = playerChunkZ + (index / diameter) - radius;

                candidates.entrySet().removeIf(entry ->
                        Math.abs(entry.getValue().x() - playerChunkX) > radius
                        || Math.abs(entry.getValue().z() - playerChunkZ) > radius);

                // Do not force-load chunks: only inspect chunks already loaded by the client.
                var chunk = mc.world.getChunkManager().getChunk(
                        cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk != null) {
                    int redstone = 0, powered = 0, artificialLight = 0;
                    int minY = Math.max(mc.world.getBottomY(), mc.player.getBlockY() - 32);
                    int maxY = mc.player.getBlockY() + 16;
                    net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable();

                    for (int y = minY; y <= maxY; y++) {
                        for (int x = cx * 16; x < cx * 16 + 16; x++) {
                            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                                pos.set(x, y, z);
                                var state = chunk.getBlockState(pos);
                                var block = state.getBlock();
                                boolean redstoneBlock =
                                        block == net.minecraft.block.Blocks.REDSTONE_WIRE ||
                                        block == net.minecraft.block.Blocks.REDSTONE_TORCH ||
                                        block == net.minecraft.block.Blocks.REDSTONE_WALL_TORCH ||
                                        block == net.minecraft.block.Blocks.REPEATER ||
                                        block == net.minecraft.block.Blocks.COMPARATOR ||
                                        block == net.minecraft.block.Blocks.OBSERVER ||
                                        block == net.minecraft.block.Blocks.PISTON ||
                                        block == net.minecraft.block.Blocks.STICKY_PISTON ||
                                        block == net.minecraft.block.Blocks.DISPENSER ||
                                        block == net.minecraft.block.Blocks.DROPPER ||
                                        block == net.minecraft.block.Blocks.HOPPER ||
                                        block == net.minecraft.block.Blocks.REDSTONE_LAMP;
                                if (redstoneBlock) {
                                    redstone++;
                                    if ((state.isOf(net.minecraft.block.Blocks.REDSTONE_WIRE)
                                            && state.get(net.minecraft.block.RedstoneWireBlock.POWER) > 0)
                                            || (state.contains(net.minecraft.state.property.Properties.LIT)
                                            && state.get(net.minecraft.state.property.Properties.LIT))
                                            || (state.contains(net.minecraft.state.property.Properties.POWERED)
                                            && state.get(net.minecraft.state.property.Properties.POWERED))
                                            || (state.contains(net.minecraft.state.property.Properties.EXTENDED)
                                            && state.get(net.minecraft.state.property.Properties.EXTENDED))) powered++;
                                }
                                if (y < mc.player.getBlockY()
                                        && mc.world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos) > 0
                                        && !state.isAir()
                                        && block != net.minecraft.block.Blocks.TORCH
                                        && block != net.minecraft.block.Blocks.WALL_TORCH
                                        && block != net.minecraft.block.Blocks.LANTERN
                                        && block != net.minecraft.block.Blocks.SOUL_TORCH
                                        && block != net.minecraft.block.Blocks.SOUL_LANTERN
                                        && block != net.minecraft.block.Blocks.GLOWSTONE
                                        && block != net.minecraft.block.Blocks.SHROOMLIGHT
                                        && block != net.minecraft.block.Blocks.SEA_LANTERN) artificialLight++;
                            }
                        }
                    }
                    if (redstone >= 3 || artificialLight >= 2) {
                        candidates.put(net.minecraft.util.math.ChunkPos.toLong(cx, cz),
                                new Detection(cx, cz, redstone, powered, artificialLight));
                    }
                }

                // At most one alert every three seconds, always choosing the nearest known hit.
                if (tick % 60 == 0 && !candidates.isEmpty()) {
                    Detection nearest = candidates.values().stream()
                            .filter(d -> !reported.contains(net.minecraft.util.math.ChunkPos.toLong(d.x(), d.z())))
                            .min(java.util.Comparator.comparingLong(d ->
                                    (long)(d.x() - playerChunkX) * (d.x() - playerChunkX)
                                    + (long)(d.z() - playerChunkZ) * (d.z() - playerChunkZ)))
                            .orElse(null);
                    if (nearest != null) {
                        reported.add(net.minecraft.util.math.ChunkPos.toLong(nearest.x(), nearest.z()));
                        double distance = Math.hypot(nearest.x() - playerChunkX, nearest.z() - playerChunkZ) * 16.0;
                        Notifications.info("Nearby Suspicious Chunk",
                                nearest.x() + ", " + nearest.z() + " | " + Math.round(distance)
                                        + "m | redstone " + nearest.redstone()
                                        + " (active " + nearest.powered() + "), light " + nearest.artificialLight(), "!");
                    }
                }
            }
        });

        armorAlert = add(new Feature("Armor Alert", "Notifies when equipped armor is nearly broken", Feature.Category.UTILITY, true) {
            private int ticks;
            @Override
            void onTick(MinecraftClient mc) {
                if (++ticks % 100 != 0 || mc.player == null) return;
                for (var stack : mc.player.getArmorItems()) {
                    if (!stack.isEmpty() && stack.isDamageable()) {
                        int left = stack.getMaxDamage() - stack.getDamage();
                        if (left <= Math.max(8, stack.getMaxDamage() / 10)) {
                            Notifications.info("Armor Alert", stack.getName().getString() + " is at "
                                    + Math.max(0, left * 100 / stack.getMaxDamage()) + "%", "!");
                        }
                    }
                }
            }
        });

        restore(cfg, fps, true);
        restore(cfg, ping, true);
        restore(cfg, clock, true);
        restore(cfg, coords, false);
        restore(cfg, keystrokes, true);
        restore(cfg, keybindList, true);
        restore(cfg, armorStatus, true);
        restore(cfg, direction, true);
        restore(cfg, biome, false);
        restore(cfg, speedometer, false);
        restore(cfg, worldTime, false);
        restore(cfg, foodStatus, false);
        restore(cfg, autoTool, false);
        restore(cfg, armorAlert, false);
        restore(cfg, suspiciousChunkFinder, false);
    }

    private static Feature hud(String name, String description) {
        return add(new Feature(name, description, Feature.Category.UTILITY, true));
    }

    private static Feature add(Feature f) {
        ALL.add(f);
        return f;
    }

    private static void restore(Config cfg, Feature f, boolean defaultOn) {
        Boolean saved = cfg.modules.get(f.name);
        f.restore(saved != null ? saved : defaultOn);
    }

    static List<Feature> byCategory(Feature.Category category) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : ALL) {
            if (f.category == category) {
                out.add(f);
            }
        }
        return out;
    }

    static void tick(MinecraftClient mc) {
        for (Feature f : ALL) {
            f.tick(mc);
        }
    }

    static void resetAll(MinecraftClient mc) {
        for (Feature f : ALL) {
            f.reset(mc);
        }
    }

    static void updateAnims(double dt) {
        for (Feature f : ALL) {
            f.anim.update(dt);
        }
    }
}

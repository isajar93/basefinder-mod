package com.h4ck3r.basefinder;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.*;

public class BaseFinderRenderer {

    public static final Color C_CHEST = new Color(255, 140, 0);
    public static final Color C_FURNACE = new Color(169, 169, 169);
    public static final Color C_BED = new Color(220, 20, 60);
    public static final Color C_SHULKER = new Color(255, 0, 255);
    public static final Color C_STORAGE = new Color(0, 191, 255);
    public static final Color C_REDSTONE = new Color(255, 215, 0);
    public static final Color C_VALUABLE = new Color(0, 255, 127);
    public static final Color C_DEFAULT = new Color(0, 255, 255);

    public static final Set<Block> BASE_BLOCKS = Set.of(
        Blocks.CHEST, Blocks.TRAPPED_CHEST,
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.BARREL, Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER,
        Blocks.BREWING_STAND, Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.ENDER_CHEST,
        Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX,
        Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX,
        Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX,
        Blocks.WHITE_BED, Blocks.ORANGE_BED, Blocks.MAGENTA_BED, Blocks.LIGHT_BLUE_BED,
        Blocks.YELLOW_BED, Blocks.LIME_BED, Blocks.PINK_BED, Blocks.GRAY_BED,
        Blocks.LIGHT_GRAY_BED, Blocks.CYAN_BED, Blocks.PURPLE_BED, Blocks.BLUE_BED,
        Blocks.BROWN_BED, Blocks.GREEN_BED, Blocks.RED_BED, Blocks.BLACK_BED,
        Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.CRAFTING_TABLE, Blocks.JUKEBOX, Blocks.NOTE_BLOCK,
        Blocks.DAYLIGHT_DETECTOR, Blocks.COMPARATOR, Blocks.REPEATER,
        Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
        Blocks.REDSTONE_BLOCK, Blocks.TNT,
        Blocks.NETHERITE_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK
    );

    public static final Map<Long, FoundBlock> GLOBAL_CACHE = new ConcurrentHashMap<>();
    public static final List<FoundBlock> RENDER_LIST = new ArrayList<>();

    private static long lastScan = 0;
    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BaseFinder-Scanner");
        t.setDaemon(true);
        return t;
    });
    private static Future<?> currentScan = null;

    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        long now = System.currentTimeMillis();

        if (now - lastScan > 1000 && (currentScan == null || currentScan.isDone())) {
            lastScan = now;
            currentScan = SCAN_EXECUTOR.submit(() -> scanAsync(client));
        }

        Vec3d cam = context.camera().getPos();
        BlockPos pp = client.player.getBlockPos();
        RENDER_LIST.clear();

        for (FoundBlock fb : GLOBAL_CACHE.values()) {
            double dist = Math.sqrt(pp.getSquaredDistance(fb.pos));
            if (dist <= BaseFinderMod.maxDistance) {
                fb.renderDistance = dist;
                RENDER_LIST.add(fb);
            }
        }
        RENDER_LIST.sort((a, b) -> Double.compare(b.renderDistance, a.renderDistance));

        if (RENDER_LIST.isEmpty()) return;

        Matrix4f mat = context.matrixStack().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (FoundBlock f : RENDER_LIST) {
            Color c = getColor(f.block);
            float a = f.renderDistance < 64 ? 1.0f : Math.max(0.2f, 1.0f - (float)(f.renderDistance / BaseFinderMod.maxDistance));

            double x = f.pos.getX() - cam.x;
            double y = f.pos.getY() - cam.y;
            double z = f.pos.getZ() - cam.z;

            drawBox(b, mat, x, y, z, c, a);
            if (BaseFinderMod.showTracers) {
                drawTracer(b, mat, 0, 0, 0, x+0.5, y+0.5, z+0.5, c, a * 0.4f);
            }
        }

        t.draw();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void scanAsync(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        BlockPos pp = client.player.getBlockPos();
        ChunkPos cp = new ChunkPos(pp);
        int r = BaseFinderMod.scanRadius;
        int maxSq = BaseFinderMod.maxCacheDistance * BaseFinderMod.maxCacheDistance;

        Set<Long> currentScanKeys = new HashSet<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkPos chunkPos = new ChunkPos(cp.x + dx, cp.z + dz);
                WorldChunk chunk;
                try {
                    chunk = client.world.getChunk(chunkPos.x, chunkPos.z);
                } catch (Exception e) { continue; }

                if (chunk == null) continue;

                chunk.forEachBlockMatchingPredicate(
                    s -> BASE_BLOCKS.contains(s.getBlock()),
                    (pos, state) -> {
                        double dist = pp.getSquaredDistance(pos);
                        if (dist <= maxSq) {
                            long key = pos.asLong();
                            currentScanKeys.add(key);
                            if (!GLOBAL_CACHE.containsKey(key)) {
                                GLOBAL_CACHE.put(key, new FoundBlock(pos, state.getBlock(), Math.sqrt(dist)));
                            }
                        }
                        return true;
                    }
                );
            }
        }

        int cleanDist = BaseFinderMod.maxCacheDistance + 5000;
        int cleanDistSq = cleanDist * cleanDist;
        GLOBAL_CACHE.entrySet().removeIf(e -> {
            FoundBlock fb = e.getValue();
            return pp.getSquaredDistance(fb.pos) > cleanDistSq;
        });
    }

    private static void drawBox(BufferBuilder b, Matrix4f m, double x, double y, double z, Color c, float a) {
        float r = c.getRed()/255f, g = c.getGreen()/255f, bl = c.getBlue()/255f;
        float minX = (float)x, maxX = (float)(x+1);
        float minY = (float)y, maxY = (float)(y+1);
        float minZ = (float)z, maxZ = (float)(z+1);

        line(b, m, minX, minY, minZ, maxX, minY, minZ, r, g, bl, a);
        line(b, m, maxX, minY, minZ, maxX, minY, maxZ, r, g, bl, a);
        line(b, m, maxX, minY, maxZ, minX, minY, maxZ, r, g, bl, a);
        line(b, m, minX, minY, maxZ, minX, minY, minZ, r, g, bl, a);
        line(b, m, minX, maxY, minZ, maxX, maxY, minZ, r, g, bl, a);
        line(b, m, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, bl, a);
        line(b, m, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, bl, a);
        line(b, m, minX, maxY, maxZ, minX, maxY, minZ, r, g, bl, a);
        line(b, m, minX, minY, minZ, minX, maxY, minZ, r, g, bl, a);
        line(b, m, maxX, minY, minZ, maxX, maxY, minZ, r, g, bl, a);
        line(b, m, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, bl, a);
        line(b, m, minX, minY, maxZ, minX, maxY, maxZ, r, g, bl, a);
    }

    private static void drawTracer(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, Color c, float a) {
        float r = c.getRed()/255f, g = c.getGreen()/255f, bl = c.getBlue()/255f;
        line(b, m, (float)x1, (float)y1, (float)z1, (float)x2, (float)y2, (float)z2, r, g, bl, a);
    }

    private static void line(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, a).next();
        b.vertex(m, x2, y2, z2).color(r, g, bl, a).next();
    }

    private static Color getColor(Block block) {
        String k = block.getTranslationKey();
        if (k.contains("chest") && !k.contains("trapped")) return C_CHEST;
        if (k.contains("trapped_chest")) return new Color(255, 69, 0);
        if (k.contains("furnace") || k.contains("smoker") || k.contains("blast")) return C_FURNACE;
        if (k.contains("bed")) return C_BED;
        if (k.contains("shulker")) return C_SHULKER;
        if (k.contains("barrel") || k.contains("hopper") || k.contains("dispenser") || k.contains("dropper")) return C_STORAGE;
        if (k.contains("comparator") || k.contains("repeater") || k.contains("observer") || k.contains("piston") || k.contains("redstone")) return C_REDSTONE;
        if (k.contains("netherite") || k.contains("diamond") || k.contains("emerald") || k.contains("beacon")) return C_VALUABLE;
        return C_DEFAULT;
    }

    public static String getColorName(Block block) {
        String k = block.getTranslationKey();
        if (k.contains("chest")) return "COFRE";
        if (k.contains("furnace") || k.contains("smoker")) return "HORNO";
        if (k.contains("bed")) return "CAMA";
        if (k.contains("shulker")) return "SHULKER";
        if (k.contains("barrel") || k.contains("hopper")) return "ALMACEN";
        if (k.contains("redstone") || k.contains("comparator") || k.contains("repeater")) return "REDSTONE";
        if (k.contains("diamond") || k.contains("netherite") || k.contains("emerald")) return "VALIOSO";
        return "OTRO";
    }

    public static class FoundBlock {
        public final BlockPos pos;
        public final Block block;
        public final double foundDistance;
        public double renderDistance;
        public final long foundTime;

        FoundBlock(BlockPos p, Block b, double d) {
            pos = p;
            block = b;
            foundDistance = d;
            renderDistance = d;
            foundTime = System.currentTimeMillis();
        }
    }
}

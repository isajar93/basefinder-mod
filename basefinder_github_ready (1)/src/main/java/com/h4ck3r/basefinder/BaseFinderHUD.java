package com.h4ck3r.basefinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.*;
import java.util.stream.Collectors;

public class BaseFinderHUD {

    private static final int HUD_X = 5;
    private static final int HUD_Y = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int BG_COLOR = 0x88000000;
    private static final int BORDER_COLOR = 0xFF00FFFF;

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        BlockPos pp = client.player.getBlockPos();
        List<BaseFinderRenderer.FoundBlock> nearby = BaseFinderRenderer.RENDER_LIST.stream()
            .filter(fb -> fb.renderDistance <= 500)
            .sorted(Comparator.comparingDouble(fb -> fb.renderDistance))
            .limit(15)
            .collect(Collectors.toList());

        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> totalCounts = new HashMap<>();
        for (BaseFinderRenderer.FoundBlock fb : BaseFinderRenderer.GLOBAL_CACHE.values()) {
            String name = BaseFinderRenderer.getColorName(fb.block);
            totalCounts.merge(name, 1, Integer::sum);
            if (fb.renderDistance <= 500) {
                counts.merge(name, 1, Integer::sum);
            }
        }

        int totalGlobal = BaseFinderRenderer.GLOBAL_CACHE.size();
        int totalNearby = nearby.size();

        int lines = 4 + counts.size() + Math.min(nearby.size(), 10) + 2;
        int width = 220;
        int height = lines * LINE_HEIGHT + 10;

        context.fill(HUD_X, HUD_Y, HUD_X + width, HUD_Y + height, BG_COLOR);
        context.drawBorder(HUD_X, HUD_Y, width, height, BORDER_COLOR);

        int y = HUD_Y + 5;
        int x = HUD_X + 5;

        drawText(context, "§b§l[BASEFINDER V2] §r§7| 10k+ CHUNKS", x, y);
        y += LINE_HEIGHT + 2;

        String estado = BaseFinderMod.enabled ? "§aACTIVO" : "§cINACTIVO";
        String tracers = BaseFinderMod.showTracers ? "§aON" : "§cOFF";
        drawText(context, "§7Estado: " + estado + " §7| Tracers: " + tracers + " §7| Radio: §e" + BaseFinderMod.scanRadius + "c", x, y);
        y += LINE_HEIGHT + 2;

        drawText(context, "§7Cache Global: §e" + totalGlobal + " §7bloques | Nearby: §e" + totalNearby, x, y);
        y += LINE_HEIGHT + 2;

        drawText(context, "§8§m                                    ", x, y);
        y += LINE_HEIGHT;

        drawText(context, "§b§lCATEGORIAS (500b):", x, y);
        y += LINE_HEIGHT;

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String color = getCategoryColor(entry.getKey());
            drawText(context, " " + color + entry.getKey() + ": §f" + entry.getValue() + " §7(Total: " + totalCounts.getOrDefault(entry.getKey(), 0) + ")", x, y);
            y += LINE_HEIGHT;
        }

        y += 2;
        drawText(context, "§8§m                                    ", x, y);
        y += LINE_HEIGHT;

        drawText(context, "§b§lBASES CERCANAS (Top 10):", x, y);
        y += LINE_HEIGHT;

        int shown = 0;
        for (BaseFinderRenderer.FoundBlock fb : nearby) {
            if (shown >= 10) break;
            String name = BaseFinderRenderer.getColorName(fb.block);
            String color = getCategoryColor(name);
            String coords = "§7[" + fb.pos.getX() + ", " + fb.pos.getY() + ", " + fb.pos.getZ() + "]";
            String dist = String.format("§e%.0fm", fb.renderDistance);
            drawText(context, " " + color + "■ §r" + coords + " " + dist + " §7" + name, x, y);
            y += LINE_HEIGHT;
            shown++;
        }

        if (nearby.isEmpty()) {
            drawText(context, " §7No hay bases cercanas", x, y);
        }
    }

    private static void drawText(DrawContext context, String text, int x, int y) {
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, x, y, 0xFFFFFF);
    }

    private static String getCategoryColor(String cat) {
        return switch (cat) {
            case "COFRE" -> "§6";
            case "CAMA" -> "§c";
            case "SHULKER" -> "§d";
            case "ALMACEN" -> "§b";
            case "HORNO" -> "§7";
            case "REDSTONE" -> "§e";
            case "VALIOSO" -> "§a";
            default -> "§f";
        };
    }
}

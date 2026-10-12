/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.world.block.entity;

import de.bluecolored.bluemap.core.util.Key;

import java.util.Map;

/**
 * Reads the paint-color NBT contract shared by TrafficCraft 1.20.4-1.1.3's
 * colored blocks, traffic lights, street signs and house-number signs.
 */
public class TrafficCraftColoredBlockEntity extends BlockEntity {

    // PaintColor texture RGB values, in the serialized dye-index order (0..15).
    // These are texture multipliers, not map, firework or text colors.
    private static final int[] PAINT_COLORS = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA,
            0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA,
            0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };

    private final int paintIndex;

    protected TrafficCraftColoredBlockEntity(Map<String, Object> data) {
        super(data);
        // Minecraft CompoundTag.getInt returns zero for an absent/non-numeric tag.
        Object color = data.get("color");
        this.paintIndex = color instanceof Number ? ((Number) color).intValue() : 0;
    }

    public int getColor(Key block) {
        if (paintIndex == -1) return getDefaultColor(block);
        // TrafficCraft resolves an unknown dye index to WHITE.
        int index = paintIndex >= 0 && paintIndex < PAINT_COLORS.length ? paintIndex : 0;
        return 0xFF000000 | PAINT_COLORS[index];
    }

    private static int getDefaultColor(Key block) {
        return switch (block.getValue()) {
            case "guardrail" -> 0xFF828282;
            case "concrete_barrier" -> 0xFFABABAB;
            case "traffic_cone", "traffic_bollard", "traffic_barrel", "road_barrier_fence" -> 0xFFD12725;
            case "reflector" -> 0xFFF9FFFE;
            default -> 0xFFFFFFFF;
        };
    }

}

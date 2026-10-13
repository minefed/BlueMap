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
package de.bluecolored.bluemap.core.map.hires;

import java.util.Random;

final class PRBMTestModels {
    static TileModel fixture(int triangles) {
        TileModel model = new TileModel(triangles);
        model.add(triangles);
        Random random = new Random(20261013);
        int[] floatBits = {0, 0x80000000, 0x3f800000, 0xbf800000, 1, 0x007fffff,
                0x7f7fffff, 0xff7fffff, 0x7f800000, 0xff800000, 0x7fc00001, 0xffc01234};
        for (int i = 0; i < model.position.length; i++)
            model.position[i] = Float.intBitsToFloat(i < floatBits.length ? floatBits[i] : random.nextInt());
        for (int i = 0; i < model.uv.length; i++)
            model.uv[i] = Float.intBitsToFloat(i < floatBits.length ? floatBits[floatBits.length - 1 - i] : random.nextInt());
        for (int i = 0; i < model.color.length; i++) model.color[i] = random.nextFloat() * 4 - 2;
        for (int i = 0; i < model.ao.length; i++) model.ao[i] = random.nextFloat() * 4 - 2;
        random.nextBytes(model.blocklight);
        random.nextBytes(model.sunlight);
        int[] materials = {0, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, -1, -1, 0};
        for (int i = 0; i < triangles; i++) model.materialIndex[i] = materials[i % materials.length];
        return model;
    }
}

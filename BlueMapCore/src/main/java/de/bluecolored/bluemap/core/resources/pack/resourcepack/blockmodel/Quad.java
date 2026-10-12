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
package de.bluecolored.bluemap.core.resources.pack.resourcepack.blockmodel;

import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import de.bluecolored.bluemap.core.resources.AbstractTypeAdapterFactory;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.math.Color;

import java.io.IOException;

/** A baked client quad. Positions and sprite-local UVs use Minecraft's 0..16 units. */
@JsonAdapter(Quad.Adapter.class)
public class Quad {
    private float[][] positions;
    private float[][] uv;
    private TextureVariable texture = new TextureVariable(ResourcePack.MISSING_TEXTURE);
    private Direction direction = Direction.UP;
    private Direction cullface;
    private int tintindex = -1;
    private Color color = new Color().set(1, 1, 1, 1, false);
    private boolean shade = true;

    private Quad() {}

    public Quad copy() {
        Quad copy = new Quad();
        copy.positions = positions;
        copy.uv = uv;
        copy.texture = texture.copy();
        copy.direction = direction;
        copy.cullface = cullface;
        copy.tintindex = tintindex;
        copy.color = new Color().set(color);
        copy.shade = shade;
        return copy;
    }

    public float[][] getPositions() { return positions; }
    public float[][] getUv() { return uv; }
    public TextureVariable getTexture() { return texture; }
    public Direction getDirection() { return direction; }
    public Direction getCullface() { return cullface; }
    public int getTintindex() { return tintindex; }
    public Color getColor() { return color; }
    public boolean isShade() { return shade; }

    public void optimize(ResourcePack resourcePack) { texture.optimize(resourcePack); }

    private static void validate(float[][] values, int width, String name) throws IOException {
        if (values == null || values.length != 4) throw new IOException(name + " must contain four vertices");
        for (float[] vertex : values) {
            if (vertex == null || vertex.length != width) throw new IOException("Invalid " + name + " vertex");
            for (float value : vertex) if (!Float.isFinite(value)) throw new IOException("Non-finite " + name);
        }
    }

    static class Adapter extends AbstractTypeAdapterFactory<Quad> {
        Adapter() { super(Quad.class); }

        @Override
        public Quad read(JsonReader in, Gson gson) throws IOException {
            Quad quad = gson.getDelegateAdapter(this, TypeToken.get(Quad.class)).read(in);
            validate(quad.positions, 3, "positions");
            validate(quad.uv, 2, "uv");
            if (quad.texture == null || quad.direction == null || quad.color == null)
                throw new IOException("Quad texture, direction and color must not be null");
            if (!Float.isFinite(quad.color.r) || !Float.isFinite(quad.color.g) || !Float.isFinite(quad.color.b))
                throw new IOException("Non-finite quad color");
            if (quad.color.r < 0 || quad.color.r > 1 || quad.color.g < 0 || quad.color.g > 1 ||
                    quad.color.b < 0 || quad.color.b > 1 || quad.color.a != 1)
                throw new IOException("Quad color must be opaque RGB in the range 0..1");
            return quad;
        }
    }
}

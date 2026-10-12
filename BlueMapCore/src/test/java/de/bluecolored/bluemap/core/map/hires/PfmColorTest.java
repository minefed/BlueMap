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

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.blockmodel.ResourceModelBuilder;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.adapter.ResourcesGson;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockmodel.BlockModel;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.BlockProperties;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.*;

class PfmColorTest {
    @TempDir Path root;
    private final ResourcePack pack = new ResourcePack(22);
    private static final int[] INDICES = {0, 1, 0, -1, 1, 2};
    private static final RenderSettings SETTINGS = new RenderSettings() {
        public int getRemoveCavesBelowY() { return Integer.MIN_VALUE; }
        public int getCaveDetectionOceanFloor() { return 0; }
        public boolean isCaveDetectionUsesBlockLight() { return false; }
        public float getAmbientLight() { return 1; }
        public boolean isSaveHiresLayer() { return true; }
    };

    @Test void sinksAndBathtubSeparateMaterialAndWaterTintsInBothModelFormats() throws Exception {
        ResourceModelBuilder renderer = renderer();
        for (String id : new String[]{"basic_sink", "basic_bathtub", "acacia_kitchen_sink"}) {
            for (boolean baked : new boolean[]{false, true}) {
                Variant model = model(baked, "#ffffff");
                // Reuse the renderer across biomes to verify its per-index cache resets per block.
                for (int water : new int[]{0x234567, 0x6789AB}) {
                    TileModel tile = render(renderer, model, new TestBlock("pfm:" + id, water));
                    assertEquals(INDICES.length * 2, tile.size());
                    for (int face = 0; face < INDICES.length; face++)
                        assertColor(tile, face, INDICES[face] == 1 ? water : 0xFFFFFF, 1);
                }
            }
        }
    }

    @Test void ordinaryFurnitureAndUnsupportedLampUseWhiteInsteadOfFoliage() throws Exception {
        colors("{\"default\":\"#00ff00\"}");
        ResourceModelBuilder renderer = renderer();
        for (String id : new String[]{"oak_herringbone_planks", "basic_lamp"}) {
            for (boolean baked : new boolean[]{false, true}) {
                TileModel tile = render(renderer, model(baked, "#ffffff"), new TestBlock("pfm:" + id, 0x234567));
                for (int face = 0; face < INDICES.length; face++) assertColor(tile, face, 0xFFFFFF, 1);
            }
        }
    }

    @Test void toiletUsesDirtyStateOrWaterForEveryTintIndex() throws Exception {
        ResourceModelBuilder renderer = renderer();
        for (String state : new String[]{"dirty", "clean", "flushing", "empty"}) {
            for (boolean baked : new boolean[]{false, true}) {
                TileModel tile = render(renderer, model(baked, "#ffffff"),
                        new TestBlock("pfm:basic_toilet[toilet=" + state + "]", 0x234567));
                int tint = state.equals("dirty") ? 0x534230 : 0x234567;
                for (int face = 0; face < INDICES.length; face++)
                    assertColor(tile, face, INDICES[face] < 0 ? 0xFFFFFF : tint, 1);
            }
        }
    }

    @Test void explicitBlockColorOverrideWinsForAllIndices() throws Exception {
        colors("{\"default\":\"#00ff00\",\"pfm:basic_sink\":\"#123456\"}");
        ResourceModelBuilder renderer = renderer();
        for (boolean baked : new boolean[]{false, true}) {
            TileModel tile = render(renderer, model(baked, "#ffffff"), new TestBlock("pfm:basic_sink", 0x234567));
            for (int face = 0; face < INDICES.length; face++)
                assertColor(tile, face, INDICES[face] < 0 ? 0xFFFFFF : 0x123456, 1);
        }
    }

    @Test void bakedVertexColorMultipliesOnlyItsOwnFaceTint() throws Exception {
        TileModel tile = render(renderer(), model(true, "#808080"), new TestBlock("pfm:basic_sink", 0x234567));
        for (int face = 0; face < INDICES.length; face++)
            assertColor(tile, face, INDICES[face] == 1 ? 0x234567 : 0xFFFFFF, 128 / 255f);
    }

    @Test void transparentTexturesPreserveLowResolutionTintAndOpacityInBothModelFormats() throws Exception {
        int water = 0x234567;
        for (boolean baked : new boolean[]{false, true}) {
            TileModel tile = new TileModel(INDICES.length * 2);
            Color lowResolutionColor = new Color().premultiplied();
            // Texture.from encodes this half-alpha image as the PNG used by the gallery.
            renderer(0x80FFFFFF).build(new TestBlock("pfm:basic_sink", water), model(baked, "#808080"),
                    new BlockModelView(tile), lowResolutionColor);
            float multiplier = baked ? 128 / 255f : 1;
            for (int face = 0; face < INDICES.length; face++)
                assertColor(tile, face, INDICES[face] == 1 ? water : 0xFFFFFF, multiplier);
            // Four white and two water-colored upward faces contribute equal alpha weight.
            assertEquals((4 + 2 * (0x23 / 255f)) / 6 * multiplier, lowResolutionColor.r, .00001f);
            assertEquals((4 + 2 * (0x45 / 255f)) / 6 * multiplier, lowResolutionColor.g, .00001f);
            assertEquals((4 + 2 * (0x67 / 255f)) / 6 * multiplier, lowResolutionColor.b, .00001f);
            assertEquals(128 / 255f, lowResolutionColor.a, .00001f);
        }
    }

    private void colors(String json) throws Exception {
        Path file = root.resolve("blockColors.json");
        Files.writeString(file, json);
        pack.getColorCalculatorFactory().load(file);
    }

    private ResourceModelBuilder renderer() throws Exception {
        return renderer(0xFFFFFFFF);
    }

    private ResourceModelBuilder renderer(int argb) throws Exception {
        ResourcePath<Texture> path = new ResourcePath<>("pfm:block/test");
        BufferedImage white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        white.setRGB(0, 0, argb);
        path.setResource(Texture.from(path, white, null));
        pack.getTextures().put(path, path.getResource());
        TextureGallery gallery = new TextureGallery();
        gallery.put(ResourcePack.MISSING_TEXTURE);
        gallery.put(path);
        return new ResourceModelBuilder(pack, gallery, SETTINGS);
    }

    private static Variant model(boolean baked, String color) {
        StringJoiner faces = new StringJoiner(",");
        for (int index : INDICES) {
            if (baked) {
                faces.add("{\"positions\":[[0,16,16],[16,16,16],[16,16,0],[0,16,0]]," +
                        "\"uv\":[[0,16],[16,16],[16,0],[0,0]],\"texture\":\"pfm:block/test\",\"direction\":\"up\"," +
                        "\"tintindex\":" + index + ",\"color\":\"" + color + "\"}");
            } else {
                faces.add("{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{" +
                        "\"texture\":\"#surface\",\"tintindex\":" + index + "}}}");
            }
        }
        String field = baked ? "bluemap_quads" : "elements";
        BlockModel model = ResourcesGson.INSTANCE.fromJson("{\"ambientocclusion\":false," +
                "\"textures\":{\"surface\":\"pfm:block/test\"},\"" + field + "\":[" + faces + "]}", BlockModel.class);
        Variant variant = ResourcesGson.INSTANCE.fromJson("{\"model\":\"pfm:block/test\"}", Variant.class);
        variant.getModel().setResource(model);
        return variant;
    }

    private static TileModel render(ResourceModelBuilder renderer, Variant model, TestBlock block) {
        TileModel tile = new TileModel(INDICES.length * 2);
        renderer.build(block, model, new BlockModelView(tile), new Color());
        return tile;
    }

    private static void assertColor(TileModel tile, int quad, int rgb, float multiplier) {
        for (int triangle = 0; triangle < 2; triangle++) {
            int offset = (quad * 2 + triangle) * 3;
            assertEquals(((rgb >> 16) & 255) / 255f * multiplier, tile.color[offset], .00001f);
            assertEquals(((rgb >> 8) & 255) / 255f * multiplier, tile.color[offset + 1], .00001f);
            assertEquals((rgb & 255) / 255f * multiplier, tile.color[offset + 2], .00001f);
            assertTrue(tile.materialIndex[quad * 2 + triangle] > 0);
        }
    }

    private class TestBlock extends BlockNeighborhood<TestBlock> {
        private final BlockState state;
        private final Biome biome;
        TestBlock(String state, int water) {
            super(pack, SETTINGS, null, 0, 0, 0);
            this.state = BlockState.fromString(state);
            this.biome = new Biome.Default() {
                private final Color waterColor = new Color().set(water | 0xFF000000).premultiplied();
                @Override public Color getWaterColor() { return waterColor; }
            };
        }
        @Override public BlockState getBlockState() { return state; }
        @Override public Biome getBiome() { return biome; }
        @Override public LightData getLightData() { return new LightData(15, 0); }
        @Override public BlockProperties getProperties() { return BlockProperties.DEFAULT; }
        @Override public boolean isRemoveIfCave() { return false; }
        @Override public ExtendedBlock<?> getNeighborBlock(int x, int y, int z) { return this; }
    }
}

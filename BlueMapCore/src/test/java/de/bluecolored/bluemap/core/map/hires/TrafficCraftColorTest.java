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
import de.bluecolored.bluemap.core.resources.pack.datapack.DataPack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockmodel.BlockModel;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.BlockProperties;
import de.bluecolored.bluemap.core.world.Chunk;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import de.bluecolored.bluemap.core.world.block.entity.TrafficCraftColoredBlockEntity;
import de.bluecolored.bluemap.core.world.mca.MCAUtil;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;
import de.bluecolored.bluemap.core.world.mca.chunk.Chunk_1_18;
import de.bluecolored.bluenbt.NBTWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TrafficCraftColorTest {

    @TempDir Path temp;
    private MCAWorld world;
    private final ResourcePack resources = new ResourcePack(22);
    private static final RenderSettings SETTINGS = new RenderSettings() {
        public int getRemoveCavesBelowY() { return Integer.MIN_VALUE; }
        public int getCaveDetectionOceanFloor() { return 0; }
        public boolean isCaveDetectionUsesBlockLight() { return false; }
        public float getAmbientLight() { return 1; }
        public boolean isSaveHiresLayer() { return true; }
    };

    @Test
    void serializedChunkPaintsEveryDyeAtItsOwnPosition() throws Exception {
        // TrafficCraft 1.20.4-1.1.3 PaintColor texture values, independently
        // checked against the published JAR, not its firework/text palette.
        int[] expected = {16383998, 16351261, 13061821, 3847130,
                16701501, 8439583, 15961002, 4673362,
                10329495, 1481884, 8991416, 3949738,
                8606770, 6192150, 11546150, 1908001};
        Integer[] colors = new Integer[16];
        for (int i = 0; i < colors.length; i++) colors[i] = i;
        Chunk chunk = chunk("asphalt_pattern_0", "colored_block_entity", colors);
        FixtureBlock block = block(chunk, 0);
        ResourceModelBuilder renderer = renderer();
        Variant model = model(true, false);
        for (int i = 0; i < colors.length; i++) {
            block.set(i, 64, 0);
            assertInstanceOf(TrafficCraftColoredBlockEntity.class, block.getBlockEntity());
            TileModel tile = render(renderer, model, block);
            assertEquals(2, tile.size());
            assertFaceColor(tile, 0, expected[i]);
            assertFaceColor(tile, 1, expected[i]);
            assertNotEquals(0, tile.materialIndex[0], "The painted face must retain its texture");
        }
    }

    @Test
    void paintOnlyMultipliesTintedFacesAndAllTrafficCraftTintIndicesAgree() throws Exception {
        FixtureBlock block = block(chunk("asphalt_pattern_1", "colored_block_entity", 4), 0);
        TileModel tile = render(renderer(), model(true, true), block);
        assertEquals(6, tile.size());
        assertFaceColor(tile, 0, 0xFFFFFF); // unpainted asphalt under the line
        assertFaceColor(tile, 1, 0xFFFFFF);
        for (int face = 2; face < 6; face++) assertFaceColor(tile, face, 0xFED83D);
    }

    @Test
    void untintedTopFaceDoesNotInheritPreviouslyRenderedPaintInLowResolutionColor() throws Exception {
        FixtureBlock block = block(chunk("asphalt_pattern_1", "colored_block_entity", 4), 0);
        BlockModel model = ResourcesGson.INSTANCE.fromJson("{\"ambientocclusion\":false," +
                "\"textures\":{\"paint\":\"trafficcraft:block/test_paint\"},\"elements\":[" +
                "{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{" +
                "\"texture\":\"#paint\",\"tintindex\":0}}}," +
                "{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{" +
                "\"texture\":\"#paint\"}}}]}", BlockModel.class);
        Variant variant = ResourcesGson.INSTANCE.fromJson("{\"model\":\"trafficcraft:block/test\"}", Variant.class);
        variant.getModel().setResource(model);
        TileModel tile = new TileModel(4);
        Color lowResolutionColor = new Color().premultiplied();

        renderer().build(block, variant, new BlockModelView(tile), lowResolutionColor);

        assertEquals(4, tile.size());
        for (int face = 0; face < 2; face++) assertFaceColor(tile, face, 0xFED83D);
        for (int face = 2; face < 4; face++) assertFaceColor(tile, face, 0xFFFFFF);
        // Only the untinted upward face contributes to the low-resolution map color.
        assertEquals(0xFFFFFFFF, lowResolutionColor.getInt());
    }

    @Test
    void ordinaryAsphaltRemainsUntintedWithoutABlockEntity() throws Exception {
        FixtureBlock block = block(chunk("asphalt", "colored_block_entity"), 0);
        assertNull(block.getBlockEntity());
        TileModel tile = render(renderer(), model(false, false), block);
        assertEquals(2, tile.size());
        assertFaceColor(tile, 0, 0xFFFFFF);
        assertFaceColor(tile, 1, 0xFFFFFF);
    }

    @Test
    void lampTintIndicesWithoutAClientColorHandlerStayWhite() throws Exception {
        FixtureBlock lamp = block(chunk("street_lamp", "street_lamp_block_entity", (Integer) null), 0);
        assertNull(lamp.getBlockEntity()); // Its non-color entity remains outside this adapter.
        assertFaceColor(render(renderer(), model(true, false), lamp), 0, 0xFFFFFF);
    }

    @Test
    void noneRestoresBlockSpecificDefaultsIncludingInheritedConeColors() throws Exception {
        String[] ids = {"asphalt_pattern_0", "concrete_slope_pattern_1", "guardrail",
                "concrete_barrier", "traffic_cone", "traffic_bollard", "traffic_barrel",
                "reflector", "road_barrier_fence", "paint_bucket"};
        int[] expected = {0xFFFFFF, 0xFFFFFF, 0x828282, 0xABABAB, 0xD12725,
                0xD12725, 0xD12725, 0xF9FFFE, 0xD12725, 0xFFFFFF};
        for (int i = 0; i < ids.length; i++) {
            FixtureBlock block = block(chunk(ids[i], "colored_block_entity", -1), 0);
            assertFaceColor(render(renderer(), model(true, false), block), 0, expected[i]);
        }
    }

    @Test
    void allPaintableBlockEntityTypesLoadThroughTheChunkDeserializer() throws Exception {
        String[] blocks = {"guardrail", "traffic_light", "street_sign", "house_number_sign"};
        String[] types = {"colored_block_entity", "traffic_light_block_entity",
                "street_sign_block_entity", "house_number_sign_block_entity"};
        for (int i = 0; i < types.length; i++) {
            FixtureBlock block = block(chunk(blocks[i], types[i], 14), 0);
            assertInstanceOf(TrafficCraftColoredBlockEntity.class, block.getBlockEntity());
            assertFaceColor(render(renderer(), model(true, false), block), 0, 0xB02E26);
        }
    }

    @Test
    void missingAndUnknownPaintIndicesUseTrafficCraftWhiteFallback() throws Exception {
        for (Integer index : Arrays.asList(null, -2, 16, Integer.MAX_VALUE)) {
            FixtureBlock block = block(chunk("guardrail", "colored_block_entity", index), 0);
            assertFaceColor(render(renderer(), model(true, false), block), 0, 0xF9FFFE);
        }
    }

    @Test
    void explicitResourceOverrideWinsButGenericDefaultDoesNotEraseSavedPaint() throws Exception {
        Path config = temp.resolve("blockColors.json");
        Files.writeString(config, "{\"default\":\"#00ff00\",\"trafficcraft:guardrail\":\"#123456\"}");
        resources.getColorCalculatorFactory().load(config);
        FixtureBlock road = block(chunk("asphalt_pattern_0", "colored_block_entity", 11), 0);
        assertFaceColor(render(renderer(), model(true, false), road), 0, 0x3C44AA);
        FixtureBlock guardrail = block(chunk("guardrail", "colored_block_entity", 11), 0);
        assertFaceColor(render(renderer(), model(true, false), guardrail), 0, 0x123456);
    }

    @Test
    void vanillaFoliageStillUsesTheBiomeColorMap() throws Exception {
        BufferedImage green = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[65536];
        Arrays.fill(pixels, 0xFF59AE30);
        green.setRGB(0, 0, 256, 256, pixels, 0, 256);
        resources.getColorCalculatorFactory().setFoliageMap(green);
        FixtureBlock leaves = block(chunk("minecraft:oak_leaves", "colored_block_entity"), 0);
        assertFaceColor(render(renderer(), model(true, false), leaves), 0, 0x59AE30);
    }

    private ResourceModelBuilder renderer() throws Exception {
        ResourcePath<Texture> path = new ResourcePath<>("trafficcraft:block/test_paint");
        BufferedImage white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        white.setRGB(0, 0, 0xFFFFFFFF);
        Texture texture = Texture.from(path, white, null);
        path.setResource(texture);
        resources.getTextures().put(path, texture);
        TextureGallery gallery = new TextureGallery();
        gallery.put(ResourcePack.MISSING_TEXTURE);
        gallery.put(path);
        return new ResourceModelBuilder(resources, gallery, SETTINGS);
    }

    private Variant model(boolean tinted, boolean layered) {
        String tint = tinted ? ",\"tintindex\":0" : "";
        String faces = "{\"up\":{\"texture\":\"#paint\"" + tint + "}}";
        String elements = "{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":" + faces + "}";
        if (layered) {
            elements = "{\"faces\":{\"up\":{\"texture\":\"#paint\"}}}," + elements +
                    ",{\"faces\":{\"north\":{\"texture\":\"#paint\",\"tintindex\":1}}}";
        }
        BlockModel model = ResourcesGson.INSTANCE.fromJson("{\"ambientocclusion\":false," +
                "\"textures\":{\"paint\":\"trafficcraft:block/test_paint\"},\"elements\":[" + elements + "]}", BlockModel.class);
        Variant variant = ResourcesGson.INSTANCE.fromJson("{\"model\":\"trafficcraft:block/test\"}", Variant.class);
        variant.getModel().setResource(model);
        return variant;
    }

    private TileModel render(ResourceModelBuilder renderer, Variant model, FixtureBlock block) {
        TileModel tile = new TileModel(6);
        renderer.build(block, model, new BlockModelView(tile), new Color().premultiplied());
        return tile;
    }

    private static void assertFaceColor(TileModel tile, int face, int rgb) {
        assertEquals(((rgb >> 16) & 255) / 255f, tile.color[face * 3], 0.00001f);
        assertEquals(((rgb >> 8) & 255) / 255f, tile.color[face * 3 + 1], 0.00001f);
        assertEquals((rgb & 255) / 255f, tile.color[face * 3 + 2], 0.00001f);
    }

    private Chunk chunk(String blockId, String entityId, Integer... colors) throws Exception {
        if (world == null) {
            try (NBTWriter out = new NBTWriter(new GZIPOutputStream(Files.newOutputStream(temp.resolve("level.dat"))))) {
                out.beginCompound();
                out.endCompound();
            }
            world = MCAWorld.load(temp, Key.minecraft("overworld"), new DataPack(22));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Write an independent on-disk NBT fixture, including empty and multi-entry
        // compound lists, then exercise the production chunk deserializer below.
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            tag(out, 10, "");
            integer(out, "DataVersion", 3700);
            string(out, "Status", "minecraft:full");
            compoundList(out, "sections", 1);
            tag(out, 1, "Y");
            out.writeByte(4);
            tag(out, 10, "block_states");
            compoundList(out, "palette", 1);
            string(out, "Name", blockId.contains(":") ? blockId : "trafficcraft:" + blockId);
            out.writeByte(0); // palette entry
            out.writeByte(0); // block_states
            out.writeByte(0); // section
            compoundList(out, "block_entities", colors.length);
            for (int i = 0; i < colors.length; i++) {
                string(out, "id", "trafficcraft:" + entityId);
                integer(out, "x", i);
                integer(out, "y", 64);
                integer(out, "z", 0);
                if (colors[i] != null) integer(out, "color", colors[i]);
                out.writeByte(0); // block entity
            }
            out.writeByte(0); // root
        }
        Chunk_1_18.Data data = MCAUtil.BLUENBT.read(new ByteArrayInputStream(bytes.toByteArray()), Chunk_1_18.Data.class);
        return new Chunk_1_18(world, data);
    }

    private static void tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static void integer(DataOutputStream out, String name, int value) throws IOException {
        tag(out, 3, name);
        out.writeInt(value);
    }

    private static void string(DataOutputStream out, String name, String value) throws IOException {
        tag(out, 8, name);
        out.writeUTF(value);
    }

    private static void compoundList(DataOutputStream out, String name, int length) throws IOException {
        tag(out, 9, name);
        out.writeByte(10);
        out.writeInt(length);
    }

    private FixtureBlock block(Chunk chunk, int x) {
        return new FixtureBlock(chunk, x);
    }

    private class FixtureBlock extends BlockNeighborhood<FixtureBlock> {
        private final Chunk chunk;
        FixtureBlock(Chunk chunk, int x) {
            super(resources, SETTINGS, world, x, 64, 0);
            this.chunk = chunk;
        }
        @Override public Chunk getChunk() { return chunk; }
        // The fixture has no surrounding occluders, and has full daylight.
        @Override public ExtendedBlock<?> getNeighborBlock(int x, int y, int z) { return this; }
        @Override public LightData getLightData() { return new LightData(15, 0); }
        @Override public BlockProperties getProperties() { return BlockProperties.DEFAULT; }
        @Override public boolean isRemoveIfCave() { return false; }
    }

}

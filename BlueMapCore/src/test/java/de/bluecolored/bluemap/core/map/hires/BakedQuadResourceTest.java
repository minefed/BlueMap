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
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.BlockProperties;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BakedQuadResourceTest {
    @TempDir Path root;

    private static final String QUAD = """
            {"positions":[[0,8,16],[16,16,16],[16,16,0],[0,8,0]],
             "uv":[[0,16],[16,16],[16,0],[0,0]],
             "texture":"demo:alias", "direction":"up", "color":"#804020", "shade":false}
            """;
    private static final RenderSettings SETTINGS = new RenderSettings() {
        public int getRemoveCavesBelowY() { return Integer.MIN_VALUE; }
        public int getCaveDetectionOceanFloor() { return 0; }
        public boolean isCaveDetectionUsesBlockLight() { return false; }
        public float getAmbientLight() { return 1; }
        public boolean isSaveHiresLayer() { return true; }
    };

    @Test void preservesBakedGeometryUvsColorAndAtlasAlias() throws Exception {
        ResourcePack pack = pack(QUAD);
        var texture = pack.getTexture(new ResourcePath<>("demo:alias"));
        assertNotNull(texture);
        assertEquals("demo:alias", texture.getResourcePath().getFormatted());
        assertEquals(1f, texture.getColorStraight().r);
        assertEquals(0f, texture.getColorStraight().g);
        TileModel tile = render(pack, false);
        assertEquals(2, tile.size());
        assertArrayEquals(new float[]{0,.5f,1, 1,1,1, 1,1,0, 0,.5f,1, 1,1,0, 0,.5f,0},
                Arrays.copyOf(tile.position, 18));
        assertArrayEquals(new float[]{0,1, 1,1, 1,0, 0,1, 1,0, 0,0}, Arrays.copyOf(tile.uv, 12));
        assertEquals(128 / 255f, tile.color[0], .0001f);
        assertEquals(64 / 255f, tile.color[1], .0001f);
        assertEquals(32 / 255f, tile.color[2], .0001f);
        assertTrue(tile.materialIndex[0] > 0);
        assertArrayEquals(new float[]{1,1,1,1,1,1}, Arrays.copyOf(tile.ao, 6));
    }

    @Test void explicitCullfaceUsesNeighborProperties() throws Exception {
        ResourcePack pack = pack(QUAD.replace("\"direction\":\"up\"", "\"direction\":\"up\",\"cullface\":\"up\""));
        assertEquals(0, render(pack, true).size());
        assertEquals(2, render(pack, false).size());
    }

    @Test void higherPriorityAliasUsesLowerPackPngAndPreservesTextureIdentity() throws Exception {
        Path high = root.resolve("high"), low = root.resolve("low");
        write(high, "assets/minecraft/atlases/blocks.json",
                "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"demo:green\",\"sprite\":\"demo:alias\"}]}");
        png(low, "assets/demo/textures/green.png", 0xff00ff00, 1);
        ResourcePack pack = packAt(low, QUAD, List.of(high, low));
        assertEquals(0f, pack.getTexture(new ResourcePath<>("demo:alias")).getColorStraight().r);
        assertEquals(1f, pack.getTexture(new ResourcePath<>("demo:alias")).getColorStraight().g);
        assertEquals("demo:alias", pack.getTexture(new ResourcePath<>("demo:alias")).getResourcePath().getFormatted());
    }

    @Test void malformedQuadIsRejectedInsteadOfCrashingRender() {
        assertThrows(RuntimeException.class, () -> ResourcesGson.INSTANCE.fromJson(
                "{\"bluemap_quads\":[{\"positions\":[[0,0,0]],\"uv\":[[0,0]]}]}", BlockModel.class));
    }

    @Test void aliasReplacesExistingPngInRenderedTextureGallery() throws Exception {
        Path high = root.resolve("high"), low = root.resolve("low");
        write(high, "assets/minecraft/atlases/blocks.json",
                "{\"sources\":[{\"type\":\"single\",\"resource\":\"demo:green\",\"sprite\":\"demo:alias\"}]}");
        png(low, "assets/demo/textures/alias.png", 0xffff0000, 1);
        png(low, "assets/demo/textures/green.png", 0xff00ff00, 1);
        ResourcePack pack = packAt(low, QUAD, List.of(high, low));
        TileModel tile = render(pack, false);
        TextureGallery gallery = new TextureGallery();
        gallery.put(pack);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        gallery.writeTexturesFile(output);
        JsonObject material = ResourcesGson.INSTANCE.fromJson(output.toString(StandardCharsets.UTF_8), JsonElement.class)
                .getAsJsonArray().get(tile.materialIndex[0]).getAsJsonObject();
        String encoded = material.get("texture").getAsString().split(",", 2)[1];
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
        assertEquals(0xff00ff00, rendered.getRGB(0, 0), "The rendered material must use the overriding alias PNG");
        assertEquals(0f, pack.getTexturePath("demo:alias").getResource().getColorStraight().r);
        assertEquals(1f, pack.getTexturePath("demo:alias").getResource().getColorStraight().g);
    }

    @Test void inheritedQuadsKeepEachChildTextureBindingIndependent() throws Exception {
        write(root, "assets/demo/models/block/parent.json",
                "{\"textures\":{\"side\":\"demo:red\"},\"bluemap_quads\":[" +
                        QUAD.replace("demo:alias", "#side") + "]}");
        write(root, "assets/demo/models/block/red_child.json", "{\"parent\":\"demo:block/parent\"}");
        write(root, "assets/demo/models/block/green_child.json",
                "{\"parent\":\"demo:block/parent\",\"textures\":{\"side\":\"demo:green\"}}");
        png(root, "assets/demo/textures/green.png", 0xff00ff00, 1);
        ResourcePack pack = pack(QUAD);
        TileModel red = render(pack, false, "demo:block/red_child");
        TileModel green = render(pack, false, "demo:block/green_child");
        assertNotEquals(red.materialIndex[0], green.materialIndex[0]);
        assertEquals(green.materialIndex[0], render(pack, false, "demo:block/green_child").materialIndex[0]);
        assertEquals(red.materialIndex[0], render(pack, false, "demo:block/red_child").materialIndex[0]);
        BlockModel redModel = pack.getBlockModel(new ResourcePath<>("demo:block/red_child"));
        BlockModel greenModel = pack.getBlockModel(new ResourcePath<>("demo:block/green_child"));
        assertEquals("demo:red", redModel.getBluemapQuads()[0].getTexture().getTexturePath(redModel.getTextures()::get).getFormatted());
        assertEquals("demo:green", greenModel.getBluemapQuads()[0].getTexture().getTexturePath(greenModel.getTextures()::get).getFormatted());
    }

    @Test void exportedClientPackRendersEveryModelWithoutMissingMaterials() throws Exception {
        String configured = System.getenv("BLUEMAP_TEST_PACK");
        assumeTrue(configured != null && !configured.isBlank(), "Set BLUEMAP_TEST_PACK for the client export integration test");
        Path exported = Path.of(configured);
        assertTrue(Files.isDirectory(exported.resolve("assets")), "Export pack assets directory must exist");
        png(root, "assets/minecraft/textures/colormap/grass.png", 0xff00ff00, 256);
        png(root, "assets/minecraft/textures/colormap/foliage.png", 0xff00ff00, 256);
        ResourcePack pack = new ResourcePack(22);
        pack.loadResources(List.of(exported, root));
        TextureGallery gallery = new TextureGallery();
        gallery.put(pack);
        Set<String> models = new HashSet<>();
        int states = 0;
        try (var files = Files.walk(exported.resolve("assets"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                Path relative = exported.relativize(file);
                if (relative.getNameCount() < 4 || !relative.getName(2).toString().equals("blockstates")) continue;
                JsonElement json = ResourcesGson.INSTANCE.fromJson(Files.readString(file), JsonElement.class);
                collectModelReferences(json, models);
                states++;
            }
        }
        assertFalse(models.isEmpty(), "Export pack must contain blockstate model references");
        long triangles = 0;
        for (String id : models) {
            BlockModel model = pack.getBlockModel(new ResourcePath<>(id));
            assertNotNull(model, id + " is missing");
            assertNotNull(model.getBluemapQuads(), id + " did not load as a baked export model");
            Variant variant = ResourcesGson.INSTANCE.fromJson("{\"model\":\"" + id + "\"}", Variant.class);
            variant.getModel().getResource(pack::getBlockModel);
            TileModel tile = new TileModel(2);
            new ResourceModelBuilder(pack, gallery, SETTINGS).build(
                    new TestBlock(pack, false), variant, new BlockModelView(tile), new Color());
            assertEquals(model.getBluemapQuads().length * 2, tile.size(), id + " lost geometry");
            for (int q = 0; q < model.getBluemapQuads().length; q++) {
                var quad = model.getBluemapQuads()[q];
                for (int triangle = 0; triangle < 2; triangle++) {
                    int face = q * 2 + triangle;
                    assertTrue(tile.materialIndex[face] > 0, id + " uses missing texture");
                    int[] vertices = {0, triangle + 1, triangle + 2};
                    for (int v = 0; v < 3; v++) {
                        for (int axis = 0; axis < 3; axis++)
                            assertEquals(quad.getPositions()[vertices[v]][axis] / 16f,
                                    tile.position[face * 9 + v * 3 + axis], .000001f, id + " position mismatch");
                        for (int axis = 0; axis < 2; axis++)
                            assertEquals(quad.getUv()[vertices[v]][axis] / 16f,
                                    tile.uv[face * 6 + v * 2 + axis], .000001f, id + " UV mismatch");
                    }
                }
            }
            triangles += tile.size();
        }
        System.out.printf("CLIENT_EXPORT_VERIFIED blockstate_files=%d models=%d triangles=%d%n", states, models.size(), triangles);
    }

    private static void collectModelReferences(JsonElement value, Set<String> models) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(child -> collectModelReferences(child, models));
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("model")) models.add(object.get("model").getAsString());
            object.entrySet().forEach(entry -> collectModelReferences(entry.getValue(), models));
        }
    }

    private ResourcePack pack(String quad) throws Exception {
        return packAt(root, quad, List.of(root));
    }

    private ResourcePack packAt(Path path, String quad, List<Path> roots) throws Exception {
        write(path, "assets/demo/models/block/test.json", "{\"bluemap_quads\":[" + quad + "]}");
        write(path, "assets/minecraft/atlases/blocks.json",
                "{\"sources\":[{\"type\":\"single\",\"resource\":\"demo:red\",\"sprite\":\"demo:alias\"}]}");
        png(path, "assets/demo/textures/red.png", 0xffff0000, 1);
        png(path, "assets/minecraft/textures/colormap/grass.png", 0xff00ff00, 256);
        png(path, "assets/minecraft/textures/colormap/foliage.png", 0xff00ff00, 256);
        ResourcePack pack = new ResourcePack(22);
        pack.loadResources(roots);
        return pack;
    }

    private TileModel render(ResourcePack pack, boolean cull) {
        return render(pack, cull, "demo:block/test");
    }

    private TileModel render(ResourcePack pack, boolean cull, String model) {
        TextureGallery gallery = new TextureGallery();
        gallery.put(pack);
        Variant variant = ResourcesGson.INSTANCE.fromJson("{\"model\":\"" + model + "\"}", Variant.class);
        variant.getModel().getResource(pack::getBlockModel);
        TileModel tile = new TileModel(2);
        ResourceModelBuilder builder = new ResourceModelBuilder(pack, gallery, SETTINGS);
        builder.build(new TestBlock(pack, cull), variant, new BlockModelView(tile), new Color());
        return tile;
    }

    private static class TestBlock extends BlockNeighborhood<TestBlock> {
        private final boolean cull;
        TestBlock(ResourcePack pack, boolean cull) { super(pack, SETTINGS, null, 0, 0, 0); this.cull = cull; }
        @Override public LightData getLightData() { return new LightData(15, 0); }
        @Override public BlockState getBlockState() { return new BlockState("demo:test"); }
        @Override public Biome getBiome() { return Biome.DEFAULT; }
        @Override public boolean isRemoveIfCave() { return false; }
        @Override public BlockProperties getProperties() { return BlockProperties.builder().culling(cull).build(); }
        @Override public ExtendedBlock<?> getNeighborBlock(int x, int y, int z) { return this; }
    }

    private static void write(Path root, String path, String value) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, value);
    }

    private static void png(Path root, String path, int color, int size) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) image.setRGB(x, y, color);
        ImageIO.write(image, "png", file.toFile());
    }
}

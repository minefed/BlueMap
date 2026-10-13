Binary golden outputs generated with the original PRBMWriter from
BlueMap commit b6141244c2cea20748f9890ca134f27d8e8873d8 and PRBMTestModels.fixture.
The fixtures are synthetic, generated from seed 20261013; no world/player data.
They retain PRBM version 1, little-endian floats, NaN canonicalization, signed zero,
extreme integers, normalized attributes, padding and adjacent material groups.

triangles-0.prbm: 72 bytes
SHA-256 9024d4e658602a53ac78530b2bf795d2a9e82acd74e9762755bd8041672afc69
triangles-8.prbm: 840 bytes
SHA-256 135029937ef1023500cad5095dd3c7f416d537d4bc5317a04b24c0edf8127c7a

The 8193-triangle golden is represented by its SHA-256 assertion to avoid a
774320-byte fixture. All outputs were captured before changing the writer.
Source and synthetic fixtures are covered by the repository MIT license.

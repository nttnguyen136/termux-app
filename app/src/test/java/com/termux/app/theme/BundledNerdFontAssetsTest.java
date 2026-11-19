package com.termux.app.theme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

import com.termux.shared.termux.theme.TermuxFontManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that all 3 bundled Mono Nerd Fonts are packaged into the APK assets,
 * parse as genuine TrueType fonts (inspecting {@code head}, {@code maxp}, {@code hhea},
 * {@code post}, {@code OS/2}, {@code cmap}, and {@code hmtx} tables directly),
 * contain required Powerline/Nerd Font PUA glyphs, maintain strict single-cell
 * monospace advance widths, and handle corrupt custom font fallbacks safely.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BundledNerdFontAssetsTest {

    private static final int[] REQUIRED_CODEPOINTS = new int[]{
        'A', '0', 'i', 'W', 'M',
        0x276F, // ❯ heavy right-pointing angle quotation mark ornament (prompt char)
        0xE0A0, //  Powerline branch
        0xE0B0, //  Powerline right arrow
        0xE725, //  Devicons git branch
        0xF07B, //  FontAwesome folder
        0xF120  //  FontAwesome terminal
    };

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testAllThreeBundledNerdFontsExistInAssetsHavePuaGlyphsAndUniformMonoWidth() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        AssetManager assets = context.getAssets();
        List<TermuxFontManager.FontDescriptor> builtInFonts = TermuxFontManager.getBuiltInFonts();
        assertEquals(3, builtInFonts.size());

        for (TermuxFontManager.FontDescriptor descriptor : builtInFonts) {
            byte[] fontBytes;
            try (InputStream in = assets.open(descriptor.assetPath)) {
                fontBytes = readAllBytes(in);
            }

            assertTrue("Font asset too small for " + descriptor.assetPath, fontBytes.length > 500_000);
            TrueTypeInspector ttf = new TrueTypeInspector(fontBytes);

            // TrueType sfnt version 0x00010000
            assertEquals("Invalid sfnt version for " + descriptor.id, 0x00010000, ttf.sfntVersion);
            assertTrue("Expected > 5000 glyphs in " + descriptor.id, ttf.numGlyphs > 5000);
            assertEquals("post.isFixedPitch must be 1 for " + descriptor.id, 1, ttf.isFixedPitch);
            assertEquals("OS/2.panose[3] must be 9 (monospace) for " + descriptor.id, 9, ttf.panoseProportion);

            int referenceGlyphId = ttf.getGlyphId('M');
            assertTrue("Missing ASCII 'M' in " + descriptor.id, referenceGlyphId > 0);
            int expectedCellAdvance = ttf.getAdvanceWidth(referenceGlyphId);
            assertTrue("Invalid cell advance width in " + descriptor.id, expectedCellAdvance > 0);

            for (int cp : REQUIRED_CODEPOINTS) {
                int glyphId = ttf.getGlyphId(cp);
                assertTrue(
                    String.format("Font %s missing required glyph U+%04X", descriptor.id, cp),
                    glyphId > 0
                );
                int advanceWidth = ttf.getAdvanceWidth(glyphId);
                assertEquals(
                    String.format("Non-uniform advance width in %s for U+%04X (gid=%d)", descriptor.id, cp, glyphId),
                    expectedCellAdvance,
                    advanceWidth
                );
            }

            Typeface loaded = TermuxFontManager.loadTypefaceForId(context, descriptor.id, null);
            assertNotNull("Typeface null for " + descriptor.id, loaded);
        }
    }

    @Test
    public void testCustomFontFallbackOnMissingOrCorruptFileNeverThrowsAndPreservesFile() throws Exception {
        Context context = RuntimeEnvironment.getApplication();

        File missingFile = new File(tempFolder.getRoot(), "missing_font.ttf");
        Typeface fromMissing = TermuxFontManager.loadTypefaceForId(
            context, TermuxFontManager.FONT_CUSTOM, missingFile);
        assertNotNull(fromMissing);

        File corruptFile = tempFolder.newFile("corrupt_font.ttf");
        byte[] corruptBytes = "not-a-valid-truetype-font".getBytes(StandardCharsets.UTF_8);
        Files.write(corruptFile.toPath(), corruptBytes);

        Typeface fromCorrupt = TermuxFontManager.loadTypefaceForId(
            context, TermuxFontManager.FONT_CUSTOM, corruptFile);
        assertNotNull(fromCorrupt);
        assertTrue(corruptFile.isFile());
        assertArrayEquals(corruptBytes, Files.readAllBytes(corruptFile.toPath()));
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Pure-Java TrueType binary table inspector for {@code maxp}, {@code hhea}, {@code post},
     * {@code OS/2}, {@code cmap} (Format 4 & Format 12), and {@code hmtx}.
     */
    private static final class TrueTypeInspector {
        final ByteBuffer buf;
        final int sfntVersion;
        final Map<String, Integer> tableOffsets = new HashMap<>();
        final int numGlyphs;
        final int numberOfHMetrics;
        final int isFixedPitch;
        final int panoseProportion;
        final int cmapOffset;
        final int hmtxOffset;

        TrueTypeInspector(byte[] data) {
            this.buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            this.sfntVersion = buf.getInt(0);
            int numTables = buf.getShort(4) & 0xFFFF;
            int recordOffset = 12;
            for (int i = 0; i < numTables; i++) {
                byte[] tagBytes = new byte[4];
                buf.position(recordOffset);
                buf.get(tagBytes);
                String tag = new String(tagBytes, StandardCharsets.US_ASCII);
                int offset = buf.getInt(recordOffset + 8);
                tableOffsets.put(tag, offset);
                recordOffset += 16;
            }

            Integer maxp = tableOffsets.get("maxp");
            Integer hhea = tableOffsets.get("hhea");
            Integer post = tableOffsets.get("post");
            Integer os2 = tableOffsets.get("OS/2");
            Integer cmap = tableOffsets.get("cmap");
            Integer hmtx = tableOffsets.get("hmtx");
            assertNotNull("Missing maxp table", maxp);
            assertNotNull("Missing hhea table", hhea);
            assertNotNull("Missing post table", post);
            assertNotNull("Missing OS/2 table", os2);
            assertNotNull("Missing cmap table", cmap);
            assertNotNull("Missing hmtx table", hmtx);

            this.numGlyphs = buf.getShort(maxp + 4) & 0xFFFF;
            this.numberOfHMetrics = buf.getShort(hhea + 34) & 0xFFFF;
            this.isFixedPitch = buf.getInt(post + 12);
            this.panoseProportion = buf.get(os2 + 32 + 3) & 0xFF;
            this.cmapOffset = cmap;
            this.hmtxOffset = hmtx;
        }

        int getGlyphId(int codePoint) {
            int numSubtables = buf.getShort(cmapOffset + 2) & 0xFFFF;
            int format12Subtable = -1;
            int format4Subtable = -1;

            for (int i = 0; i < numSubtables; i++) {
                int rec = cmapOffset + 4 + i * 8;
                int subOffset = cmapOffset + buf.getInt(rec + 4);
                int format = buf.getShort(subOffset) & 0xFFFF;
                if (format == 12 && format12Subtable < 0) {
                    format12Subtable = subOffset;
                } else if (format == 4 && format4Subtable < 0) {
                    format4Subtable = subOffset;
                }
            }

            if (format12Subtable >= 0) {
                int gid = lookupFormat12(format12Subtable, codePoint);
                if (gid > 0) return gid;
            }
            if (format4Subtable >= 0 && codePoint <= 0xFFFF) {
                return lookupFormat4(format4Subtable, codePoint);
            }
            return 0;
        }

        private int lookupFormat12(int subOffset, int codePoint) {
            int numGroups = buf.getInt(subOffset + 12);
            int groupBase = subOffset + 16;
            for (int i = 0; i < numGroups; i++) {
                int g = groupBase + i * 12;
                int startChar = buf.getInt(g);
                int endChar = buf.getInt(g + 4);
                int startGlyph = buf.getInt(g + 8);
                if (codePoint >= startChar && codePoint <= endChar) {
                    return startGlyph + (codePoint - startChar);
                }
            }
            return 0;
        }

        private int lookupFormat4(int subOffset, int codePoint) {
            int segCountX2 = buf.getShort(subOffset + 6) & 0xFFFF;
            int segCount = segCountX2 / 2;
            int endCodesOffset = subOffset + 14;
            int startCodesOffset = endCodesOffset + segCountX2 + 2;
            int idDeltasOffset = startCodesOffset + segCountX2;
            int idRangeOffsetsOffset = idDeltasOffset + segCountX2;

            for (int i = 0; i < segCount; i++) {
                int endCode = buf.getShort(endCodesOffset + i * 2) & 0xFFFF;
                int startCode = buf.getShort(startCodesOffset + i * 2) & 0xFFFF;
                if (codePoint >= startCode && codePoint <= endCode) {
                    int idDelta = buf.getShort(idDeltasOffset + i * 2);
                    int idRangeOffsetLoc = idRangeOffsetsOffset + i * 2;
                    int idRangeOffset = buf.getShort(idRangeOffsetLoc) & 0xFFFF;
                    if (idRangeOffset == 0) {
                        return (codePoint + idDelta) & 0xFFFF;
                    }
                    int glyphIndexAddr = idRangeOffsetLoc + idRangeOffset + (codePoint - startCode) * 2;
                    int glyphId = buf.getShort(glyphIndexAddr) & 0xFFFF;
                    if (glyphId == 0) return 0;
                    return (glyphId + idDelta) & 0xFFFF;
                }
            }
            return 0;
        }

        int getAdvanceWidth(int glyphId) {
            int metricIndex = Math.min(glyphId, numberOfHMetrics - 1);
            return buf.getShort(hmtxOffset + metricIndex * 4) & 0xFFFF;
        }
    }

}

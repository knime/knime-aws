/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Jun 17, 2019 (Julian Bunzel): created
 */
package org.knime.cloud.aws.mlservices.nodes.translate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.knime.core.node.InvalidSettingsException;

/**
 * Utility class for Amazon Translate service.
 *
 * @author Julian Bunzel, KNIME GmbH, Berlin, Germany
 */
final class TranslateUtils {

    /**
     * Empty constructor.
     */
    private TranslateUtils() {
        // Nothing to do here...
    }

    /** Map of supported language and their language code/identifier */
    private static final Map<String, String> SUPPORTED_LANGS;
    static {
        Map<String, String> langMap = new LinkedHashMap<>();
        langMap.put("Afrikaans", "af");
        langMap.put("Albanian", "sq");
        langMap.put("Amharic", "am");
        langMap.put("Arabic", "ar");
        langMap.put("Armenian", "hy");
        langMap.put("Azerbaijani", "az");
        langMap.put("Bengali", "bn");
        langMap.put("Bosnian", "bs");
        langMap.put("Bulgarian", "bg");
        langMap.put("Catalan", "ca");
        langMap.put("Chinese (Simplified)", "zh");
        langMap.put("Chinese (Traditional)", "zh-TW");
        langMap.put("Croatian", "hr");
        langMap.put("Czech", "cs");
        langMap.put("Danish", "da");
        langMap.put("Dari", "fa-AF");
        langMap.put("Dutch", "nl");
        langMap.put("English", "en");
        langMap.put("Estonian", "et");
        langMap.put("Farsi (Persian)", "fa");
        langMap.put("Filipino, Tagalog", "tl");
        langMap.put("Finnish", "fi");
        langMap.put("French", "fr");
        langMap.put("French (Canada)", "fr-CA");
        langMap.put("Georgian", "ka");
        langMap.put("German", "de");
        langMap.put("Greek", "el");
        langMap.put("Gujarati", "gu");
        langMap.put("Haitian Creole", "ht");
        langMap.put("Hausa", "ha");
        langMap.put("Hebrew", "he");
        langMap.put("Hindi", "hi");
        langMap.put("Hungarian", "hu");
        langMap.put("Icelandic", "is");
        langMap.put("Indonesian", "id");
        langMap.put("Irish", "ga");
        langMap.put("Italian", "it");
        langMap.put("Japanese", "ja");
        langMap.put("Kannada", "kn");
        langMap.put("Kazakh", "kk");
        langMap.put("Korean", "ko");
        langMap.put("Latvian", "lv");
        langMap.put("Lithuanian", "lt");
        langMap.put("Macedonian", "mk");
        langMap.put("Malay", "ms");
        langMap.put("Malayalam", "ml");
        langMap.put("Maltese", "mt");
        langMap.put("Marathi", "mr");
        langMap.put("Mongolian", "mn");
        langMap.put("Norwegian (Bokmål)", "no");
        langMap.put("Pashto", "ps");
        langMap.put("Polish", "pl");
        langMap.put("Portuguese (Brazil)", "pt");
        langMap.put("Portuguese (Portugal)", "pt-PT");
        langMap.put("Punjabi", "pa");
        langMap.put("Romanian", "ro");
        langMap.put("Russian", "ru");
        langMap.put("Serbian", "sr");
        langMap.put("Sinhala", "si");
        langMap.put("Slovak", "sk");
        langMap.put("Slovenian", "sl");
        langMap.put("Somali", "so");
        langMap.put("Spanish", "es");
        langMap.put("Spanish (Mexico)", "es-MX");
        langMap.put("Swahili", "sw");
        langMap.put("Swedish", "sv");
        langMap.put("Tamil", "ta");
        langMap.put("Telugu", "te");
        langMap.put("Thai", "th");
        langMap.put("Turkish", "tr");
        langMap.put("Ukrainian", "uk");
        langMap.put("Urdu", "ur");
        langMap.put("Uzbek", "uz");
        langMap.put("Vietnamese", "vi");
        langMap.put("Welsh", "cy");

        SUPPORTED_LANGS = Collections.unmodifiableMap(langMap);
    }

    /** Map of unsupported language pairs. */
    private static final Map<String, List<String>> UNSUPPORTED_PAIRS;
    static {
        Map<String, List<String>> pairMap = new HashMap<>();
        pairMap.put("Chinese (Traditional)", Arrays.asList("Chinese (Simplified)"));
        pairMap.put("Chinese (Simplified)", Arrays.asList("Chinese (Traditional)"));
        pairMap.put("Korean", Arrays.asList("Hebrew"));
        pairMap.put("Norwegian", Arrays.asList("Arabic", "Hebrew"));
        UNSUPPORTED_PAIRS = Collections.unmodifiableMap(pairMap);
    }

    /**
     * Map of supported source languages
     *
     * @return Returns a map of supported source languages
     */
    static final Map<String, String> getSourceLanguageMap() {
        Map<String, String> srcLangs = new LinkedHashMap<>();
        srcLangs.put("Auto-detect", "auto");
        srcLangs.putAll(SUPPORTED_LANGS);
        return Collections.unmodifiableMap(srcLangs);
    }

    /**
     * Map of supported target languages
     *
     * @return Returns a map of supported target languages
     */
    static final Map<String, String> getTargetLanguageMap() {
        return SUPPORTED_LANGS;
    }

    /**
     * Throws an exception, if the language pair is not supported.
     *
     * @param sourceLang The source language
     * @param targetLang The target language
     * @throws InvalidSettingsException Thrown if the language pair is not supported
     */
    static final void checkPair(final String sourceLang, final String targetLang) throws InvalidSettingsException {
        if (sourceLang.equals(targetLang)) {
            throw new InvalidSettingsException("Source and target language cannot be the same.");
        } else if (UNSUPPORTED_PAIRS.containsKey(sourceLang)
            && UNSUPPORTED_PAIRS.get(sourceLang).contains(targetLang)) {
            throw new InvalidSettingsException(
                ("Translating from " + sourceLang + " to " + targetLang + " is not supported."));
        }
    }

}

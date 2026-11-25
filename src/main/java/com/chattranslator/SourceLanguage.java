package com.chattranslator;

/** Known MyMemory source languages recognized by the local detector. */
public enum SourceLanguage
{
    SPANISH("es"),
    PORTUGUESE("pt"),
    FRENCH("fr"),
    GERMAN("de"),
    ITALIAN("it"),
    DUTCH("nl"),
    POLISH("pl"),
    RUSSIAN("ru"),
    TURKISH("tr"),
    UKRAINIAN("uk"),
    JAPANESE("ja"),
    KOREAN("ko"),
    CHINESE_SIMPLIFIED("zh-CN"),
    ARABIC("ar"),
    DANISH("da"),
    SWEDISH("sv");

    private final String code;

    SourceLanguage(String code)
    {
        this.code = code;
    }

    public String code()
    {
        return code;
    }
}

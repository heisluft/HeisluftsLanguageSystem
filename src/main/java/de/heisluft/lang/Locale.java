package de.heisluft.lang;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public enum Locale {
	VI_VN, EN_UD, LV_LV, LA_LA, NDS_DE, HR_HR, LT_LT, GD_GB, GYA_AA, FIL_PH, NL_NL, HE_IL, JBO_EN, MN_MN, SWG_DE,
	FR_FR,
	EN_US, EN_GB, KSH_DE, FR_CA, HAW_US, GV_IM, ES_ES, GA_IE, EN_PT, IO_IDO, BE_BY, EN_AU, IT_IT, AR_SA, DE_AT, GL_ES,
	EL_GR, CS_CZ, BG_BG, TLH_AA, HI_IN, LI_LI, ES_MX, EU_ES, TZL_TZL, PL_PL, KA_GE, ES_AR, KW_GB, SE_NO, HY_AM, EN_CA,
	TH_TH, TR_TR, FI_FI, SQ_AL, DA_DK, EO_UY, IS_IS, SV_SE, FO_FO, MI_NZ, ID_ID, PT_PT, VAL_ES, NN_NO, LB_LU, DE_DE,
	PT_BR, RU_RU, SO_SO, HU_HU, SR_SP, OC_FR, MK_MK, SL_SI, NO_NO, ZH_TW, BR_FR, AF_ZA, FY_NL, ET_EE, MS_MY, AZ_AZ,
	ES_UY, AST_ES, FA_IR, SK_SK, KO_KR, MT_MT, CA_ES, ES_VE, CY_GB, EN_NZ, JA_JP, RO_RO, LOL_US, ZH_CN, UK_UA,
	UNDEFINED;

	private boolean isNoOp() {
		if(this == UNDEFINED) return true;
		for(JavaPlugin plugin : LanguageManager.INSTANCE.plugins)
			if(new File(plugin.getDataFolder() + "/lang/" + this + ".lang").exists()) return false;
		return true;
	}

	public static Locale byName(String name, Locale fallback) {
		try {
			return valueOf(name.toUpperCase(java.util.Locale.ROOT));
		} catch(Exception e) {
			return fallback;
		}
	}

	@Override
	public String toString() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	final Map<String, String> translation = new HashMap<>();

	private String format(String toFormat, Object... args) {
		for(int i = 0; i < args.length; i++)
			toFormat = toFormat.replace("%" + (i + 1), args[i].toString());
		return toFormat;
	}

	public String translate(String toTranslate, Object... args) {
		if(isNoOp()) return EN_US.translate(toTranslate, args);
		if(translation.containsKey(toTranslate))
			return ChatColor.translateAlternateColorCodes('&', format(translation.get(toTranslate), args));
		if(EN_US.translation.containsKey(toTranslate)) return EN_US.translate(toTranslate, args);
		return translation.getOrDefault("translation.error",
				EN_US.translation.getOrDefault("translation.error", toTranslate));
	}


	public void init(JavaPlugin plugin) {
		if(isNoOp()) return;
		final File f = new File(plugin.getDataFolder() + "/lang/" + this + ".lang");
		if(f.exists()) translation.putAll(LangFileParser.parse(f));
	}
}
package com.lynerrr.food.data

import androidx.annotation.StringRes
import com.lynerrr.food.R

/** How the app groups and labels the site's nutrients. */
enum class NutrientGroup(
    @StringRes val title: Int,
    @StringRes val shortTitle: Int,
    val sticker: String,
    val siteSection: String,
) {
    ENERGY(R.string.group_energy, R.string.group_energy_short, "⚡", "Пищевая ценность"),
    VITAMINS(R.string.group_vitamins, R.string.group_vitamins, "💊", "Витамины"),
    MINERALS(R.string.group_minerals, R.string.group_minerals, "🪨", "Микроэлементы"),
    AMINO_ACIDS(R.string.group_amino, R.string.group_amino, "🧬", "Незаменимые аминокислоты"),
    FATS(R.string.group_fats, R.string.group_fats, "🥑", "Жирные кислоты"),
    OTHER(R.string.group_other, R.string.group_other, "📦", "Прочее"),
}

/**
 * @param badge short code shown on the nutrient's sticker (an emoji, "B12", "Fe", "Leu"...)
 * @param primary shown by default; the rest hide behind "show more"
 */
data class NutrientInfo(
    /** The site's own spelling, so custom foods merge with site data. */
    val siteName: String,
    val group: NutrientGroup,
    val labelEn: String,
    val labelRu: String,
    val badge: String,
    val primary: Boolean,
    val order: Int,
    /** Unit used when entering this nutrient for a custom food (matches the site). */
    val unit: String,
) {
    fun label(russian: Boolean) = if (russian) labelRu else labelEn
}

object NutrientCatalog {
    private val entries = LinkedHashMap<String, NutrientInfo>()

    private class L(val site: String, val en: String, val ru: String, val badge: String)

    private fun add(group: NutrientGroup, primary: Boolean, unit: String, vararg items: L) {
        items.forEach {
            entries[nutrientKey(it.site)] = NutrientInfo(it.site, group, it.en, it.ru, it.badge, primary, entries.size, unit)
        }
    }

    init {
        val g = NutrientGroup.ENERGY
        add(g, true, "ккал", L("Калорийность", "Calories", "Калории", "🔥"))
        add(g, true, "г",
            L("Белки", "Protein", "Белки", "💪"),
            L("Жиры", "Fat", "Жиры", "🧈"),
            L("Углеводы", "Carbs", "Углеводы", "🍞"),
            L("Пищевые волокна", "Fiber", "Клетчатка", "🌾"),
            L("Моно- и дисахариды (сахара)", "Sugars", "Сахара", "🍬"),
        )
        add(g, false, "г",
            L("Вода", "Water", "Вода", "💧"),
            L("Углеводы (общие)", "Total carbs incl. fiber", "Углеводы с клетчаткой", "🍞"),
            L("Крахмал и декстрины", "Starch", "Крахмал", "🥔"),
            L("Сахароза", "Sucrose", "Сахароза", "🍬"),
            L("Глюкоза (декстроза)", "Glucose", "Глюкоза", "🍬"),
            L("Фруктоза", "Fructose", "Фруктоза", "🍬"),
            L("Лактоза", "Lactose", "Лактоза", "🥛"),
            L("Мальтоза", "Maltose", "Мальтоза", "🍬"),
            L("Зола", "Ash (mineral residue)", "Зола (минеральный остаток)", "⚪"),
        )

        val v = NutrientGroup.VITAMINS
        add(v, true, "мкг", L("Витамин А, РЭ", "Vitamin A", "Витамин A", "A"))
        add(v, true, "мг",
            L("Витамин В1, тиамин", "Thiamine", "Тиамин", "B1"),
            L("Витамин В2, рибофлавин", "Riboflavin", "Рибофлавин", "B2"),
            L("Витамин РР, НЭ", "Niacin", "Ниацин", "B3"),
            L("Витамин В4, холин", "Choline", "Холин", "B4"),
            L("Витамин В5, пантотеновая", "Pantothenic acid", "Пантотеновая кислота", "B5"),
            L("Витамин В6, пиридоксин", "Pyridoxine", "Пиридоксин", "B6"),
        )
        add(v, true, "мкг",
            L("Витамин Н, биотин", "Biotin", "Биотин", "B7"),
            L("Витамин В9, фолаты", "Folate", "Фолаты", "B9"),
            L("Витамин В12, кобаламин", "Cobalamin", "Кобаламин", "B12"),
        )
        add(v, true, "мг", L("Витамин C, аскорбиновая", "Vitamin C", "Витамин C", "C"))
        add(v, true, "мкг", L("Витамин D, кальциферол", "Vitamin D", "Витамин D", "D"))
        add(v, true, "мг", L("Витамин Е, альфа токоферол, ТЭ", "Vitamin E", "Витамин E", "E"))
        add(v, true, "мкг", L("Витамин К, филлохинон", "Vitamin K", "Витамин K", "K"))
        add(v, false, "мг",
            L("Ретинол", "Retinol", "Ретинол", "A"),
            L("бета Каротин", "Beta-carotene", "Бета-каротин", "βC"),
        )
        add(v, false, "мкг", L("Витамин D3, холекальциферол", "Vitamin D3", "Витамин D3", "D3"))
        add(v, false, "мг",
            L("гамма Токоферол", "Gamma-tocopherol", "Гамма-токоферол", "γE"),
            L("Ниацин", "Niacin (free form)", "Ниацин (свободный)", "B3"),
            L("Бетаин", "Betaine", "Бетаин", "Bet"),
        )

        val m = NutrientGroup.MINERALS
        add(m, true, "мг",
            L("Кальций, Ca", "Calcium", "Кальций", "Ca"),
            L("Магний, Mg", "Magnesium", "Магний", "Mg"),
            L("Калий, K", "Potassium", "Калий", "K"),
            L("Натрий, Na", "Sodium", "Натрий", "Na"),
            L("Фосфор, P", "Phosphorus", "Фосфор", "P"),
            L("Хлор, Cl", "Chloride", "Хлор", "Cl"),
            L("Железо, Fe", "Iron", "Железо", "Fe"),
            L("Цинк, Zn", "Zinc", "Цинк", "Zn"),
        )
        add(m, true, "мкг",
            L("Йод, I", "Iodine", "Йод", "I"),
            L("Селен, Se", "Selenium", "Селен", "Se"),
            L("Медь, Cu", "Copper", "Медь", "Cu"),
        )
        add(m, true, "мг", L("Марганец, Mn", "Manganese", "Марганец", "Mn"))
        add(m, true, "мкг",
            L("Хром, Cr", "Chromium", "Хром", "Cr"),
            L("Молибден, Mo", "Molybdenum", "Молибден", "Mo"),
            L("Фтор, F", "Fluoride", "Фтор", "F"),
        )
        add(m, false, "мг",
            L("Сера, S", "Sulfur", "Сера", "S"),
            L("Кремний, Si", "Silicon", "Кремний", "Si"),
        )
        add(m, false, "мкг",
            L("Кобальт, Co", "Cobalt", "Кобальт", "Co"),
            L("Бор, B", "Boron", "Бор", "B"),
            L("Ванадий, V", "Vanadium", "Ванадий", "V"),
            L("Литий, Li", "Lithium", "Литий", "Li"),
            L("Никель, Ni", "Nickel", "Никель", "Ni"),
            L("Алюминий, Al", "Aluminium", "Алюминий", "Al"),
            L("Рубидий, Rb", "Rubidium", "Рубидий", "Rb"),
            L("Стронций, Sr", "Strontium", "Стронций", "Sr"),
            L("Титан, Ti", "Titanium", "Титан", "Ti"),
            L("Цирконий, Zr", "Zirconium", "Цирконий", "Zr"),
        )

        val a = NutrientGroup.AMINO_ACIDS
        add(a, true, "г",
            L("Лейцин", "Leucine", "Лейцин", "Leu"),
            L("Изолейцин", "Isoleucine", "Изолейцин", "Ile"),
            L("Валин", "Valine", "Валин", "Val"),
            L("Лизин", "Lysine", "Лизин", "Lys"),
            L("Метионин", "Methionine", "Метионин", "Met"),
            L("Метионин + Цистеин", "Methionine + cysteine", "Метионин + цистеин", "M+C"),
            L("Треонин", "Threonine", "Треонин", "Thr"),
            L("Триптофан", "Tryptophan", "Триптофан", "Trp"),
            L("Фенилаланин+Тирозин", "Phenylalanine + tyrosine", "Фенилаланин + тирозин", "F+Y"),
            L("Гистидин", "Histidine", "Гистидин", "His"),
        )
        add(a, false, "г",
            L("Фенилаланин", "Phenylalanine", "Фенилаланин", "Phe"),
            L("Аргинин", "Arginine", "Аргинин", "Arg"),
            L("Тирозин", "Tyrosine", "Тирозин", "Tyr"),
            L("Цистеин", "Cysteine", "Цистеин", "Cys"),
            L("Глутаминовая кислота", "Glutamic acid", "Глутаминовая кислота", "Glu"),
            L("Аспарагиновая кислота", "Aspartic acid", "Аспарагиновая кислота", "Asp"),
            L("Аланин", "Alanine", "Аланин", "Ala"),
            L("Глицин", "Glycine", "Глицин", "Gly"),
            L("Пролин", "Proline", "Пролин", "Pro"),
            L("Гидроксипролин", "Hydroxyproline", "Гидроксипролин", "Hyp"),
            L("Серин", "Serine", "Серин", "Ser"),
        )

        val f = NutrientGroup.FATS
        add(f, true, "г",
            L("Насыщенные жирные кислоты", "Saturated", "Насыщенные", "SFA"),
            L("Мононенасыщенные жирные кислоты", "Monounsaturated", "Мононенасыщенные", "MUFA"),
            L("Полиненасыщенные жирные кислоты", "Polyunsaturated", "Полиненасыщенные", "PUFA"),
            L("Омега-3 жирные кислоты", "Omega-3", "Омега-3", "Ω3"),
            L("Омега-6 жирные кислоты", "Omega-6", "Омега-6", "Ω6"),
            L("Трансжиры", "Trans fats", "Трансжиры", "TFA"),
        )
        add(f, true, "мг", L("Холестерин", "Cholesterol", "Холестерин", "Chol"))
        add(f, false, "г",
            L("18:3 Омега-3, альфа-линоленовая", "ALA (omega-3)", "АЛК (омега-3)", "ALA"),
            L("20:5 Омега-3 , эйкозапентаеновая (ЭПК)", "EPA (omega-3)", "ЭПК (омега-3)", "EPA"),
            L("22:5 Омега-3, докозапентаеновая (ДПК)", "DPA (omega-3)", "ДПК (омега-3)", "DPA"),
            L("22:6 Омега-3, докозагексаеновая (ДГК),", "DHA (omega-3)", "ДГК (омега-3)", "DHA"),
            L("Моноеновые трансжиры", "Monoene trans fats", "Моноеновые трансжиры", "TFA"),
        )
        add(f, false, "мг", L("бета Ситостерол", "Beta-sitosterol", "Бета-ситостерол", "βS"))
    }

    private val FATTY_ACID = Regex("""^(\d+:\d+)\s*(.*)$""")

    /** Info for a site nutrient name; unknown names are classified by their site section and hidden by default. */
    fun info(name: String, section: String): NutrientInfo {
        entries[nutrientKey(name)]?.let { return it }
        FATTY_ACID.matchEntire(name)?.let { match ->
            val (code, rest) = match.destructured
            // Sort individual fatty acids by chain length after the named ones.
            val (carbons, bonds) = code.split(':').map { it.toInt() }
            val label = rest.trim(',', ' ').ifEmpty { code }
            return NutrientInfo(name, NutrientGroup.FATS, label, label, code, false, 10_000 + carbons * 10 + bonds, "г")
        }
        val group = when (section) {
            "Витамины" -> NutrientGroup.VITAMINS
            "Макроэлементы", "Микроэлементы" -> NutrientGroup.MINERALS
            "Незаменимые аминокислоты", "Заменимые аминокислоты" -> NutrientGroup.AMINO_ACIDS
            "Жирные кислоты", "Стеролы (стерины)" -> NutrientGroup.FATS
            "Пищевая ценность", "Усвояемые углеводы" -> NutrientGroup.ENERGY
            else -> NutrientGroup.OTHER
        }
        return NutrientInfo(name, group, name, name, name.take(2), false, 20_000, "г")
    }

    /** Info by site name alone, for screens that only know the nutrient's name. */
    fun info(name: String): NutrientInfo = info(name, "")

    /** Nutrients offered when entering a custom food, in display order. */
    fun editable(group: NutrientGroup): List<NutrientInfo> = entries.values.filter { it.group == group && it.primary }
}

/** A nutrient ready to display, with its catalog info. */
data class GroupedNutrient(val value: NutrientValue, val info: NutrientInfo)

data class NutrientGroupUi(
    val group: NutrientGroup,
    val primary: List<GroupedNutrient>,
    val more: List<GroupedNutrient>,
) {
    private val tracked get() = primary.filter { it.value.target != null || it.value.limit != null }
    val trackedCount get() = tracked.size
    /** Nutrients at ≥90% of target (and not over a limit), or under their limit when there's only a limit. */
    val onTrackCount get() = tracked.count { (v) ->
        !v.overLimit && (v.target == null || v.amount >= v.target * 0.9)
    }
}

/** Splits nutrient values into display groups. Hidden "more" rows only include nutrients actually present. */
fun groupNutrients(values: List<NutrientValue>): List<NutrientGroupUi> {
    val grouped = values.map { GroupedNutrient(it, NutrientCatalog.info(it.name, it.section)) }
        .sortedBy { it.info.order }
        .groupBy { it.info.group }
    return NutrientGroup.entries.mapNotNull { group ->
        val items = grouped[group] ?: return@mapNotNull null
        val (primary, more) = items.partition { it.info.primary }
        val shownMore = more.filter { it.value.amount > 0 }
        if (primary.isEmpty() && shownMore.isEmpty()) null else NutrientGroupUi(group, primary, shownMore)
    }
}

/** The site's units are Russian; English shows the usual abbreviations. */
fun displayUnit(unit: String, russian: Boolean): String = if (russian) unit else when (unit) {
    "г" -> "g"
    "мг" -> "mg"
    "мкг" -> "µg"
    "ккал" -> "kcal"
    else -> unit
}

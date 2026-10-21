package com.lynerrr.food.data

/**
 * Well-known good sources of a nutrient. [query] is a Russian search for the food in the site's catalog;
 * null when it isn't a food (sunlight, supplements).
 */
data class CuratedSource(val sticker: String, val en: String, val ru: String, val query: String?)

data class CuratedSources(val items: List<CuratedSource>, val noteEn: String? = null, val noteRu: String? = null)

object BestSources {
    private fun s(sticker: String, en: String, ru: String, query: String? = ru.lowercase()) = CuratedSource(sticker, en, ru, query)

    private val liver = s("🫀", "Beef liver", "Говяжья печень", "печень говяжья")
    private val eggs = s("🥚", "Eggs", "Яйца", "яйцо куриное")
    private val milk = s("🥛", "Milk", "Молоко")
    private val cheese = s("🧀", "Hard cheese", "Твёрдый сыр", "сыр")
    private val beef = s("🥩", "Beef", "Говядина")
    private val chicken = s("🍗", "Chicken breast", "Куриная грудка", "курица грудка")
    private val salmon = s("🐟", "Salmon", "Лосось")
    private val tuna = s("🐟", "Tuna", "Тунец")
    private val sardines = s("🐟", "Sardines", "Сардины", "сардина")
    private val mackerel = s("🐟", "Mackerel", "Скумбрия")
    private val herring = s("🐟", "Herring", "Сельдь")
    private val mussels = s("🦪", "Mussels", "Мидии")
    private val oysters = s("🦪", "Oysters", "Устрицы")
    private val spinach = s("🥬", "Spinach", "Шпинат")
    private val broccoli = s("🥦", "Broccoli", "Брокколи")
    private val avocado = s("🥑", "Avocado", "Авокадо")
    private val lentils = s("🫘", "Lentils", "Чечевица")
    private val beans = s("🫘", "Beans", "Фасоль")
    private val chickpeas = s("🫘", "Chickpeas", "Нут")
    private val buckwheat = s("🌾", "Buckwheat", "Гречка", "гречневая")
    private val oats = s("🌾", "Oats", "Овсянка", "овсяные хлопья")
    private val almonds = s("🌰", "Almonds", "Миндаль")
    private val cashews = s("🌰", "Cashews", "Кешью")
    private val sunflower = s("🌻", "Sunflower seeds", "Семена подсолнечника", "семена подсолнечника")
    private val pumpkinSeeds = s("🎃", "Pumpkin seeds", "Тыквенные семечки", "семена тыквы")
    private val sesame = s("🌱", "Sesame", "Кунжут")
    private val parsley = s("🌿", "Parsley", "Петрушка")
    private val mushrooms = s("🍄", "Mushrooms", "Грибы", "шампиньоны")
    private val potatoes = s("🥔", "Potatoes", "Картофель")
    private val bananas = s("🍌", "Bananas", "Бананы", "банан")
    private val peanuts = s("🥜", "Peanuts", "Арахис")
    private val darkChocolate = s("🍫", "Dark chocolate", "Горький шоколад", "шоколад горький")
    private val supplements = s("💊", "Fortified foods or supplements", "Обогащённые продукты или добавки", null)

    private val byNutrient: Map<String, CuratedSources> = mapOf(
        "Витамин А, РЭ" to CuratedSources(listOf(
            liver, s("🥕", "Carrots", "Морковь"), s("🍠", "Sweet potato", "Батат"), s("🎃", "Pumpkin", "Тыква"),
            spinach, s("🧈", "Butter", "Сливочное масло", "масло сливочное"), eggs,
        )),
        "Витамин В1, тиамин" to CuratedSources(listOf(
            s("🥓", "Pork", "Свинина"), sunflower, s("🫛", "Peas", "Горох"), buckwheat, oats, beans,
            s("🍞", "Whole-grain bread", "Цельнозерновой хлеб", "хлеб цельнозерновой"),
        )),
        "Витамин В2, рибофлавин" to CuratedSources(listOf(liver, milk, cheese, eggs, mushrooms, almonds, beef)),
        "Витамин РР, НЭ" to CuratedSources(listOf(chicken, tuna, salmon, beef, peanuts, liver, mushrooms)),
        "Витамин В4, холин" to CuratedSources(listOf(eggs, liver, beef, salmon, chicken, s("🫘", "Soybeans", "Соя"), broccoli)),
        "Витамин В5, пантотеновая" to CuratedSources(listOf(liver, mushrooms, eggs, avocado, sunflower, chicken, milk)),
        "Витамин В6, пиридоксин" to CuratedSources(listOf(tuna, salmon, chicken, potatoes, bananas, chickpeas, sunflower)),
        "Витамин Н, биотин" to CuratedSources(listOf(eggs, liver, sunflower, peanuts, almonds, s("🍠", "Sweet potato", "Батат"), salmon)),
        "Витамин В9, фолаты" to CuratedSources(listOf(
            spinach, lentils, chickpeas, broccoli, avocado, liver, s("🌱", "Asparagus", "Спаржа"), parsley,
        )),
        "Витамин В12, кобаламин" to CuratedSources(
            listOf(liver, mussels, sardines, mackerel, beef, eggs, milk, cheese, supplements),
            "Found almost only in animal foods. On a vegan diet, use fortified foods or a supplement.",
            "Есть почти только в продуктах животного происхождения. Веганам нужны обогащённые продукты или добавка.",
        ),
        "Витамин C, аскорбиновая" to CuratedSources(listOf(
            s("🫑", "Bell pepper", "Сладкий перец", "перец сладкий"), s("🌹", "Rose hips", "Шиповник"),
            s("🫐", "Blackcurrant", "Чёрная смородина", "смородина черная"), s("🥝", "Kiwi", "Киви"),
            s("🍊", "Oranges", "Апельсины", "апельсин"), broccoli, s("🍓", "Strawberries", "Клубника"),
            s("🥬", "Sauerkraut", "Квашеная капуста", "капуста квашеная"),
        )),
        "Витамин D, кальциферол" to CuratedSources(
            listOf(salmon, herring, mackerel, s("🐟", "Cod liver", "Печень трески", "печень трески"), eggs, mushrooms,
                s("☀️", "Sunlight on skin", "Солнечный свет", null), supplements),
            "Hard to get from food alone, especially in winter at northern latitudes.",
            "Из еды его получить сложно, особенно зимой в северных широтах.",
        ),
        "Витамин Е, альфа токоферол, ТЭ" to CuratedSources(listOf(
            sunflower, almonds, s("🌻", "Sunflower oil", "Подсолнечное масло", "масло подсолнечное"),
            s("🌰", "Hazelnuts", "Фундук"), avocado, spinach,
        )),
        "Витамин К, филлохинон" to CuratedSources(listOf(
            spinach, parsley, broccoli, s("🥬", "Brussels sprouts", "Брюссельская капуста", "капуста брюссельская"),
            s("🌿", "Dill", "Укроп"), s("🥗", "Lettuce", "Салат листовой", "салат"),
        )),
        "Кальций, Ca" to CuratedSources(listOf(
            cheese, s("🍶", "Cottage cheese", "Творог"), milk, s("🥛", "Kefir", "Кефир"), sesame, sardines, almonds,
            s("⬜", "Tofu", "Тофу"),
        )),
        "Магний, Mg" to CuratedSources(listOf(pumpkinSeeds, almonds, cashews, spinach, darkChocolate, buckwheat, beans, avocado)),
        "Калий, K" to CuratedSources(listOf(
            potatoes, bananas, beans, avocado, spinach, s("🍑", "Dried apricots", "Курага"), s("🍅", "Tomatoes", "Томаты", "томат"),
        )),
        "Натрий, Na" to CuratedSources(
            listOf(s("🧂", "Table salt", "Поваренная соль", "соль"), s("🌭", "Sausages and cured meats", "Колбасы", "колбаса"),
                cheese, s("🍞", "Bread", "Хлеб"), s("🥒", "Pickles", "Солёные огурцы", "огурцы соленые")),
            "Most people get too much. These are the main sources to watch.",
            "Обычно его едят слишком много. Это главные источники, за которыми стоит следить.",
        ),
        "Фосфор, P" to CuratedSources(listOf(cheese, salmon, chicken, beef, pumpkinSeeds, lentils, milk)),
        "Железо, Fe" to CuratedSources(
            listOf(liver, beef, mussels, lentils, buckwheat, spinach, pumpkinSeeds),
            "Iron from plants absorbs better with vitamin C in the same meal.",
            "Железо из растений лучше усваивается вместе с витамином C.",
        ),
        "Цинк, Zn" to CuratedSources(listOf(oysters, beef, pumpkinSeeds, cheese, cashews, chickpeas, oats)),
        "Йод, I" to CuratedSources(listOf(
            s("🌊", "Seaweed (kelp)", "Морская капуста", "морская капуста"), s("🐟", "Cod", "Треска"),
            s("🧂", "Iodized salt", "Йодированная соль", "соль"), milk, eggs, s("🦐", "Shrimp", "Креветки"),
        )),
        "Селен, Se" to CuratedSources(listOf(
            s("🌰", "Brazil nuts", "Бразильский орех", "бразильский орех"), tuna, sardines, eggs, chicken, sunflower,
        )),
        "Медь, Cu" to CuratedSources(listOf(liver, oysters, cashews, sesame, darkChocolate, sunflower, mushrooms)),
        "Марганец, Mn" to CuratedSources(listOf(
            mussels, s("🌰", "Hazelnuts", "Фундук"), oats, s("🍚", "Brown rice", "Бурый рис", "рис бурый"), chickpeas,
            spinach, s("🍍", "Pineapple", "Ананас"),
        )),
        "Омега-3 жирные кислоты" to CuratedSources(listOf(
            salmon, mackerel, herring, sardines, s("🌱", "Flaxseed", "Семена льна", "семена льна"),
            s("🌱", "Chia seeds", "Семена чиа", "чиа"), s("🌰", "Walnuts", "Грецкий орех", "грецкий орех"),
            s("🫒", "Linseed oil", "Льняное масло", "масло льняное"),
        )),
        "Пищевые волокна" to CuratedSources(listOf(
            lentils, beans, s("🌾", "Bran", "Отруби"), oats, s("🍐", "Pears", "Груши", "груша"),
            s("🫐", "Raspberries", "Малина"), broccoli,
        )),
        "Белки" to CuratedSources(listOf(
            chicken, s("🍶", "Cottage cheese", "Творог"), eggs, tuna, beef, lentils, cheese, s("⬜", "Tofu", "Тофу"),
        )),
    )

    fun curated(siteName: String): CuratedSources? = byNutrient[siteName]
}

// Builds the food database bundled with the app by scraping health-diet.ru once from a PC.
// Run: ./gradlew :scraper:run              (everything, everyday foods first; resumable; then writes the asset)
//      ./gradlew :scraper:run --args=common  (everyday foods only)
//      ./gradlew :scraper:run --args=build (only rebuild the asset from what's already scraped)
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    // Share the app's parser so scraped data matches what the app reads; the site client lives only here.
    sourceSets["main"].kotlin.srcDir("../app/src/main/java/com/lynerrr/food/net")
}

dependencies {
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.json:json:20260719")
}

application {
    mainClass.set("com.lynerrr.food.scraper.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

// Packaged products (Pepsi, Oreo, ...) from Open Food Facts: ./gradlew :scraper:runProducts
tasks.register<JavaExec>("runProducts") {
    group = "application"
    description = "Builds products/products.dat from Open Food Facts"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.lynerrr.food.scraper.products.ProductsMainKt")
    workingDir = rootDir
}

// USDA FoodData Central SR Legacy (public domain) as a second food source: ./gradlew :scraper:runUsda
tasks.register<JavaExec>("runUsda") {
    group = "application"
    description = "Builds app/src/main/assets/usda.dat from USDA SR Legacy"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.lynerrr.food.scraper.usda.UsdaMainKt")
    workingDir = rootDir
}

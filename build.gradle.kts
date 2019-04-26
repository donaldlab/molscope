import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
	kotlin("jvm") version "1.3.21"
}


group = "edu.duke.cs"
version = "0.1"

repositories {
	jcenter()
}

dependencies {
	compile(kotlin("stdlib-jdk8"))
	compile("cuchaz:kludge")
}

configure<JavaPluginConvention> {
	sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {

	kotlinOptions {

		jvmTarget = "1.8"

		// enable experimental features
		languageVersion = "1.3"
		freeCompilerArgs += "-XXLanguage:+InlineClasses"
	}
}

tasks {

	val compileShaders by creating {
		group = "build"
		doLast {

			val outDir = buildDir.resolve("shaders")

			val inDir = file("src/main/glsl")
			fileTree(inDir)
				.matching {
					include("**/*.vert")
					include("**/*.geom")
					include("**/*.frag")
					include("**/*.comp")
				}
				.forEach { inFile ->
					val inFileRel = inFile.relativeTo(inDir)
					val outFile = inFileRel.resolveSibling(inFileRel.name + ".spv")
					exec {
						this.workingDir = outDir
						commandLine(
							"glslc",
							"-o", outFile.path,
							inFile.absolutePath
						)
					}
				}
		}
	}

	this["build"].dependsOn(compileShaders)
}

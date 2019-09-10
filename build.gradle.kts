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

	testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.0")
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

// Make a value that can be set after processResources has configured itself.
// ie, the value can be set after we know about the task graph,
// and we can tell if this a dev or release build
class ResourceVal<T>(var value: T) {
	override fun toString() = value.toString()
}
val isDev = ResourceVal(true)

gradle.taskGraph.whenReady {

	// assume we're doing a release build if we're calling the jar task
	if (hasTask(":jar")) {
		isDev.value = false
	}
}

tasks {

	val compileShaders by creating {
		group = "build"
		doLast {

			val outDir = sourceSets["main"].resources.srcDirs.first()
				.resolve("${project.group.toString().replace(".", "/")}/${project.name}/shaders")

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

	// tell gradle to write down the version number where the app can read it
	processResources {

		from(sourceSets["main"].resources.srcDirs) {
			include("**/build.properties")
			expand(
				"version" to "$version",
				"dev" to isDev
			)
		}
	}

	// add documentation to the jar file
	jar {
		into("") { // project root
			from("readme.md")
			from("LICENSE.txt")
			// TODO: contributing.md?
		}
	}
}

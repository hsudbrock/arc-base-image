import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
	kotlin("jvm") version "2.1.10"
	kotlin("plugin.spring") version "2.1.10"
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.citi.helm") version "2.2.0"
	id("com.citi.helm-publish") version "2.2.0"
}

group = "de.hsudbrock"
version = "0.1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenLocal()
	mavenCentral()
	maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
	val arcVersion = "0.1.0-SNAPSHOT"
	val langchain4jVersion = "0.36.2"

	implementation("org.eclipse.lmos:arc-spring-boot-starter:$arcVersion")
	implementation("org.eclipse.lmos:arc-graphql-spring-boot-starter:$arcVersion")

	// Langchain4j
	implementation("dev.langchain4j:langchain4j-bedrock:$langchain4jVersion")
	implementation("dev.langchain4j:langchain4j-google-ai-gemini:$langchain4jVersion")
	implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")
	implementation("dev.langchain4j:langchain4j-open-ai:$langchain4jVersion")

	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter-actuator")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

helm {
	charts {
		create("main") {
			chartName.set("${rootProject.name}-chart")
			chartVersion.set("${project.version}")
			sourceDir.set(file("src/main/helm"))
		}
	}
}

tasks.register("replaceChartVersion") {
	doLast {
		val chartFile = file("src/main/helm/Chart.yaml")
		val content = chartFile.readText()
		val updatedContent = content.replace("\${chartVersion}", "${project.version}")
		chartFile.writeText(updatedContent)
	}
}

tasks.register("helmPush") {
	description = "Push Helm chart to OCI registry"
	group = "helm"
	dependsOn(tasks.named("helmPackageMainChart"))

	doLast {
		val registryUrl = getProperty("REGISTRY_URL")
		val registryUsername = getProperty("REGISTRY_USERNAME")
		val registryPassword = getProperty("REGISTRY_PASSWORD")
		val registryNamespace = getProperty("REGISTRY_NAMESPACE")

		helm.execHelm("registry", "login") {
			option("-u", registryUsername)
			option("-p", registryPassword)
			args(registryUrl)
		}

		helm.execHelm("push") {
			args(tasks.named("helmPackageMainChart").get().outputs.files.singleFile.toString())
			args("oci://$registryUrl/$registryNamespace")
		}

		helm.execHelm("registry", "logout") {
			args(registryUrl)
		}
	}
}

tasks.named<BootBuildImage>("bootBuildImage") {
	val sysRegUrl = System.getenv("REGISTRY_URL")
	if (project.hasProperty("REGISTRY_URL") || sysRegUrl != null) {
		val registryUrl = getProperty("REGISTRY_URL")
		val registryUsername = getProperty("REGISTRY_USERNAME")
		val registryPassword = getProperty("REGISTRY_PASSWORD")
		val registryNamespace = getProperty("REGISTRY_NAMESPACE")

		imageName.set("$registryUrl/$registryNamespace/${rootProject.name}:${project.version}")
		publish = true
		docker {
			publishRegistry {
				url.set(registryUrl)
				username.set(registryUsername)
				password.set(registryPassword)
			}
		}
	} else {
		imageName.set("${rootProject.name}:${project.version}")
		publish = false
	}
}

fun getProperty(propertyName: String) = System.getenv(propertyName) ?: project.findProperty(propertyName) as String
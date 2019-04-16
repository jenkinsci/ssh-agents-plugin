def buildConfiguration = buildPlugin.recommendedConfigurations()

// Also build on recent weekly
buildConfiguration << [ platform: "linux",   jdk: "11", jenkins: "2.170", javaLevel: "8" ]
buildConfiguration << [ platform: "windows", jdk: "11", jenkins: "2.170", javaLevel: "8" ]

buildPlugin(configurations: buildConfiguration)
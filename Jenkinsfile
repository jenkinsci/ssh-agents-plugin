//def buildConfiguration = buildPlugin.recommendedConfigurations()

def buildConfiguration = [
  [ platform: "linux",   jdk: "8", jenkins: "2.190.1", javaLevel: "8" ],
  [ platform: "windows", jdk: "8", jenkins: "2.190.1", javaLevel: "8" ],
  [ platform: "linux",   jdk: "11", jenkins: "2.190.1", javaLevel: "8" ],
  [ platform: "windows", jdk: "11", jenkins: "2.190.1", javaLevel: "8" ],
  // Also build on recent weekly
  [ platform: "linux",   jdk: "11", jenkins: "2.197", javaLevel: "8" ],
  [ platform: "windows", jdk: "11", jenkins: "2.197", javaLevel: "8" ]
]

buildPlugin(configurations: buildConfiguration)
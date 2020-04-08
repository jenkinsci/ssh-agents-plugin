//def buildConfiguration = buildPlugin.recommendedConfigurations()

def buildConfiguration = [
  [platform: 'linux',   jdk: '8'],
  [platform: 'windows', jdk: '8'],
  [platform: 'linux',   jdk: '11', jenkins: '2.222.1'],
]

buildPlugin(configurations: buildConfiguration)

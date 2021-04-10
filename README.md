# (Abandoned) Heislufts Language System
A Bukkit plugin API designed to make sending localized messages as easy as possible.

## Usage:
### Add HeisluftsLanguageSystem via
- a downloaded jar from [My Website](https://heisluft.de/downloads.php)
- maven: 
```xml
<!--Your Stuff...-->
<repositories>
  <!--Your Stuff...-->
  <repository>
    <id>heisluft-repo</id>
    <url>https://heisluft.de/maven/</url>
  </repository>
  <!--Your Stuff...-->
</repositories>
<!--Your Stuff...-->
<dependencies>
  <!--Your Stuff...-->
  <dependency>
    <groupId>de.heisluft</groupId>
    <artifactId>HeisluftsLanguageSystem</artifactId>
    <version>1.0.0</version>
    <scope>compile</scope>
  </dependency>
  <!--Your Stuff...-->
</dependencies>
<!--Your Stuff...-->
````
- gradle:
```gradle
// Your Stuff...
repositories {
  // Your Stuff...
  maven {
    url 'https://heisluft.de/maven/'
  }
  // Your Stuff...
}
// Your Stuff...
dependencies {
  // Your Stuff...
  compile group: 'de.heisluft", name: "HeisluftsLanguageSystem", version: "1.0.0'
  // Your Stuff...
}
// Your Stuff...
```

### To use HeisluftsLanguageSystem, register your plugin like this:
```java
public class MyPlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		Some.init();
		LanguageManager.INSTANCE.registerPlugin(this);
		Other.init();
	}
}
```
### Then, send a localized message like so:
```java	
public void exampleMessage(Player player) {
	int someArg = 2;
	LanguageManager.INSTANCE.translate("some.unloaclized.text", player, "MyArg", player.getName(), someArg);
}
```
Suppose that you have put a file named en_us.lang in your folder containing the following:
```text
some.unlocalized.text=Some localized text with arg %1, sent to %2 having a score of %3
```
and the Player is named "SuperCrafter2000"
the player will be sent this message:<br>
Some localized text with arg MyArg, sent to SuperCrafter2000 having a score of 2

## Limitations:
heislufts Language System uses the packets sent from the client to the server. However, these are sent <b>AFTER</b>
the PlayerJoinEvent. While the Language System wont crash (it will just return "en_us") trying to send a localized
message at this point is, in fact, a waste of resources.

## Credits:
Credits belong to the author (heisluft), all contributors, as well as dmulloy2 for his TinyProtocol implementation
that this plugin is based on.

## License:
This work is licenced under the GNU General Public License v3.

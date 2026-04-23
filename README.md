# Herd Immunity App

This is a Java-based app which should run nicely on all platforms. It demonstrates Herd Immunity
by showing a table of snooker balls semi-randomly bouncing into each other. Each bounce represents
a potential transmission event. We can run simulations with an R0 of our choice, and vaccinate a
proportion of the population (meaning they count as contacts, but make no onward transmission).

Things we can demonstrate or discuss:

* Vaccinating a percentage of the population protects more than that population, because you 
  protected those people's contacts.
  
* Each run (R0, vaccination) causes a new sample to be plotted on a graph, where the x-axis
  is vaccination, and we have a separate graph for each R0, showing a best fit line. Runs are 
  stochastic, so this start a discussion about sampling and data collection.
  
* Also we can discuss to what extent this is a helpful model. Clearly people don't behave like
  the particles, yet we can still demonstrate some principles in an entertaining way. All
  models are broken; some are useful...
  
The app can run in a standalone way, with mouse clicks to choose the parameters and run the model.
It can also be controlled by an [android app](https://github.com/mrc-ide/public-events-herd-immunity-android_v2).
  
# Pre-requestites

* You need Java - any version from 8 onwards will be fine.
* To run with androids, you also need to be running a small web server.

## The web server

If you want to run the Herd Immunity with the remote android, you'll need to run a 
small web server. I have been using Abyss, as it's simple, GUI-managed, and free.

For all platforms, you'll need to download the X1 server from [here](https://aprelium.com/abyssws/download.php)
and a recent PHP bundle from [here](https://aprelium.com/downloads/). 

### Main Web server 

On windows, the installer is very automatic - I chose *C:\Abyss* to install into, and said
yes to the option to install as a windows service. 

For Mac, it is also well guided, but so you know what to expect:-

* Install the main server by dragging it into your Applications folder. 
* Run the **Abyss Web Server** within that folder. You may have to give your password a couple of times.
* Confirm that you're happy running the app, which didn't come from the app store.
* Confirm that you're happy opening up **ports below 1024**, which a web server needs to do.
* Optional - with the app open, see the **Server menu**, **Startup Configuration**, and set automatic startup.

### PHP Support

It's easy on both Windows and MAC - In Win I install into *C:\Abyss\PHP8* for example, and for Mac, it
automatically installed into a *PHP8* folder in Applications. We'll proceed now assuming those paths:

* If the installer hasn't done it already, browse to 127.0.0.1:9999
* You can choose your install language. I am going for English.
* You then choose a user/password to maintain the web server with. You won't need it that much, but make a note!
* On Mac, close Safari and browse to 127.0.0.1:9999 at this point, with your new creds.
* In the middle, you'll see "Default Host Running on Port 80" - click *Configure* next to it, and find the *Scripting Parameters* button.
* Check that *Enable Scripts Execution* is ticked - it is by default I think. Then under *Interpreters* click *Add*.
* Set Interface to *FastCGI (Local - Pipes)*.
* Browse for the interpreter - for Windows it's *C:\Abyss\PHP8\php-cgi.exe*, for Mac it's */Applications/PHP8/bin/php-cgi*.
* In Associated Extensions, click Add, and add "php". Then click OK, and OK again, and you should be at the big menu.
* Click on *Index Files*, *Add* and `index.php`. *Ok* and *Ok*.
* Press the *Restart* button at the top. Wait for about 10 seconds.

### The web content

* Download the tiny website [here](https://mrcdata.dide.ic.ac.uk/resources/barcode_epi_web_1.2.zip)
* Remove the default website in *C:\Abyss\htdocs* on windows, or */Applications/Abyss Web Server/htdocs" on Mac.
* Make a folder *epi* inside *htdocs*
* Unzip the contents of the above into *epi*.
* Test that browsing to *127.0.0.1/epi* gets you to the MRC Centenary image.

## Java

You'll need to be able to run java, and compile the code, so you need OpenJDK.
See the instructions [here](https://github.com/mrc-ide/public-events-zombie-sim2-java) 
which cover installing a JDK, including Homebrew on Mac if needed.

The end result is we want `java` and `javac` to do something useful on the command-line
or in a terminal.

## The App itself

* Clone this repo.
* Run *compile.bat* on Windows, or *./compile.sh* on Mac.
* Then to run locally, *run_local.bat* or *./run_local.sh*
* or to wait for android commands, *run_android.bat* or *./run_android.sh*


cd ..\..\
mkdir gui\deploy

:: edit iss file -> AppVersion=0.3.2.1

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion=0.3.2.1 and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
:: Build with jdk1.8.0_40 fails but jdk1.8.0_3 works
call "C:\Program Files\Java\jdk1.8.0_31\bin\javapackager.exe" -deploy -BappVersion=0.3.2.1 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "gui\deploy\Bitsquare.jar" -outfile Bitsquare -Bruntime="C:\Program Files\Java\jdk1.8.0_31\jre"

:: 32 bit build
:: call "C:\Program Files (x86)\Java\jdk1.8.0_31\bin\javapackager.exe" -deploy -BappVersion=0.3.2.1 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "gui\deploy\Bitsquare.jar" -outfile Bitsquare -Bruntime="C:\Program Files (x86)\Java\jdk1.8.0_31\jre"

cd package\windows
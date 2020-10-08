# Sound-Physics
This 1.16.3 port is just based off of the 1.15.2 port without major changes so it should be easier to maintain.

Most features should work but just like the 1.15.2 port, there are no config gui and the config has be changed in the file, and i didn't test server support (it probably doesn't work).

1.16.3 Releases should be alongside the 1.12.2 and 1.15.2 ones in the releases link below.

A Minecraft mod that provides realistic sound attenuation, reverberation, and absorption through blocks.

Downloads are in the [releases tab](https://github.com/djpadbit/Sound-Physics/releases)

This is a fork of a fork! I forked it from daipenger who forked it from sonicether, daipenger ported it to 1.12.2 and cleaned up the codebase, i just added some stuff.

The stuff added in this fork:
* Automatic stero to mono downmixing of sounds (So the original resourcepack is not needed anymore)
* More compatibility with mods (Computronics & Immersive Railroading)
* Server-side support (right position for entity and computronics sounds and higher distance before sound cutoff)

Todo:
* Rewrite Dynamic environement evaluation (feature removed for now)
* More mod compatibility ? I'm open to suggestions
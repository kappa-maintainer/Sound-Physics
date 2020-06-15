# Sound-Physics
This 1.15.2 port took quite some time to make because of the all changes in minecraft and forge from 1.12.2. I must warn you though, it is pretty hacky.

Most features should work but there are no config gui and the config has be changed in the file, and i didn't test server support (it probably doesn't work).

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
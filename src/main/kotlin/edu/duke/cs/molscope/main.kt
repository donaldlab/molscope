package edu.duke.cs.molscope

import cuchaz.kludge.tools.*
import edu.duke.cs.molscope.molecule.*
import edu.duke.cs.molscope.view.SpaceFilling


fun main() = autoCloser {

	// make an N-terminal alanine molecule
	val mol = Molecule(
		"N-terminal Alanine",
		Atoms().apply {
			add(Atom(Element.Nitrogen, "N",   14.699, 27.060, 24.044)) // 0
			add(Atom(Element.Hydrogen, "H1",  15.468, 27.028, 24.699)) // 1
			add(Atom(Element.Hydrogen, "H2",  15.072, 27.114, 23.102)) // 2
			add(Atom(Element.Hydrogen, "H3",  14.136, 27.880, 24.237)) // 3
			add(Atom(Element.Carbon,   "CA",  13.870, 25.845, 24.199)) // 4
			add(Atom(Element.Hydrogen, "HA",  14.468, 24.972, 23.937)) // 5
			add(Atom(Element.Carbon,   "CB",  13.449, 25.694, 25.672)) // 6
			add(Atom(Element.Hydrogen, "HB1", 12.892, 24.768, 25.807)) // 7
			add(Atom(Element.Hydrogen, "HB2", 14.334, 25.662, 26.307)) // 8
			add(Atom(Element.Hydrogen, "HB3", 12.825, 26.532, 25.978)) // 9
			add(Atom(Element.Carbon,   "C",   12.685, 25.887, 23.222)) // 10
			add(Atom(Element.Oxygen,   "O",   11.551, 25.649, 23.607)) // 11
		},
		Bonds().apply {
			add(0, 1) // N-H1
			add(0, 2) // N-H2
			add(0, 3) // N-H3
			add(0, 4) // N-CA
			add(4, 5) // CA-HA
			add(4,6 ) // CA-CB
			add(6, 7) // CB-HB1
			add(6, 8) // CB-HB2
			add(6, 9) // CB-HB3
			add(6, 10) // CA-C
			add(10, 11) // C-O
		}
	)

	// open a window
	val win = Window(
		width = 800,
		height = 600,
		title = "MolScope"
	).autoClose()

	// prepare a slide for the molecule
	val slide = Slide("Slide")
	slide.lock {
		views.add(SpaceFilling(mol))
		camera.lookAtEverything()
	}
	win.slides.add(slide)

	win.waitForClose()

} // end of scope here cleans up all autoClose() resources

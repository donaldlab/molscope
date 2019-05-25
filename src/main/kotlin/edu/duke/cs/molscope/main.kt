package edu.duke.cs.molscope

import cuchaz.kludge.tools.*
import edu.duke.cs.molscope.molecule.*
import edu.duke.cs.molscope.view.BallAndStick
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
			add(4, 6) // CA-CB
			add(6, 7) // CB-HB1
			add(6, 8) // CB-HB2
			add(6, 9) // CB-HB3
			add(4, 10) // CA-C
			add(10, 11) // C-O
		}
	)

	// make a dipeptide
	val dipeptide = Polymer("GLU-ILE Dipeptide").apply {
		atoms.apply {
			add(Atom(Element.Nitrogen, "N",   12.926, 26.240, 21.956)) // 0
			add(Atom(Element.Hydrogen, "H",   13.852, 26.300, 21.559)) // 1
			add(Atom(Element.Carbon,   "CA",  11.887, 26.268, 20.919)) // 2
			add(Atom(Element.Hydrogen, "HA",  10.976, 26.735, 21.302)) // 3
			add(Atom(Element.Carbon,   "CB",  12.419, 27.131, 19.778)) // 4
			add(Atom(Element.Hydrogen, "2HB", 12.708, 28.110, 20.162)) // 5
			add(Atom(Element.Hydrogen, "3HB", 13.313, 26.637, 19.397)) // 6
			add(Atom(Element.Carbon,   "CG",  11.406, 27.343, 18.646)) // 7
			add(Atom(Element.Hydrogen, "2HG", 10.922, 26.393, 18.399)) // 8
			add(Atom(Element.Hydrogen, "3HG", 10.622, 28.022, 18.999)) // 9
			add(Atom(Element.Carbon,   "CD",  12.088, 27.904, 17.388)) // 10
			add(Atom(Element.Oxygen,   "OE1", 13.342, 27.880, 17.332)) // 11
			add(Atom(Element.Oxygen,   "OE2", 11.353, 28.290, 16.460)) // 12
			add(Atom(Element.Carbon,   "C",   11.569, 24.847, 20.413)) // 13
			add(Atom(Element.Oxygen,   "O",   12.441, 24.182, 19.845)) // 14

			add(Atom(Element.Nitrogen, "N",   10.337, 24.382, 20.639)) // 15
			add(Atom(Element.Hydrogen, "H",    9.687, 24.994, 21.110)) // 16
			add(Atom(Element.Carbon,   "CA",   9.771, 23.183, 20.000)) // 17
			add(Atom(Element.Hydrogen, "HA",  10.555, 22.429, 19.908)) // 18
			add(Atom(Element.Carbon,   "CB",   8.610, 22.575, 20.829)) // 19
			add(Atom(Element.Hydrogen, "HB",   7.790, 23.295, 20.855)) // 20
			add(Atom(Element.Carbon,   "CG2",  8.115, 21.280, 20.152)) // 21
			add(Atom(Element.Hydrogen, "1HG2", 7.230, 20.907, 20.662)) // 22
			add(Atom(Element.Hydrogen, "2HG2", 7.834, 21.470, 19.117)) // 23
			add(Atom(Element.Hydrogen, "3HG2", 8.890, 20.512, 20.180)) // 24
			add(Atom(Element.Carbon,   "CG1",  9.037, 22.275, 22.287)) // 25
			add(Atom(Element.Hydrogen, "2HG1", 9.753, 21.453, 22.299)) // 26
			add(Atom(Element.Hydrogen, "3HG1", 9.527, 23.148, 22.714)) // 27
			add(Atom(Element.Carbon,   "CD1",  7.864, 21.935, 23.216)) // 28
			add(Atom(Element.Hydrogen, "1HD1", 8.234, 21.813, 24.235)) // 29
			add(Atom(Element.Hydrogen, "2HD1", 7.128, 22.742, 23.201)) // 30
			add(Atom(Element.Hydrogen, "3HD1", 7.384, 21.006, 22.910)) // 31
			add(Atom(Element.Carbon,   "C",    9.313, 23.581, 18.589)) // 32
			add(Atom(Element.Oxygen,   "O",    8.222, 24.116, 18.417)) // 33
		}
		bonds.apply {
			add(0, 1) // N-H
			add(0, 2) // N-CA
			add(2, 3) // CA-HA
			add(2, 4) // CA-CB
			add(4, 5) // CB-2HB
			add(4, 6) // CB-3HB
			add(4, 7) // CB-CG
			add(7, 8) // CG-2HG
			add(7, 9) // CG-3HG
			add(7, 10) // CG-CD
			add(10, 11) // CD-OE1
			add(10, 12) // CD-OE2
			add(2, 13) // CA-C
			add(13, 14) // C-O

			add(13, 15) // C-N

			add(15, 16) // N-H
			add(15, 17) // N-CA
			add(17, 18) // CA-HA
			add(17, 19) // CA-CB
			add(19, 20) // CB-HB
			add(19, 21) // CB-CG2
			add(21, 22) // CG2-1HG2
			add(21, 23) // CG2-2HG2
			add(21, 24) // CG2-3HG2
			add(19, 25) // CB-CG1
			add(25, 26) // CG1-2HG1
			add(25, 27) // CG1-3HG1
			add(25, 28) // CG1-CD1
			add(28, 29) // CD1-1HD1
			add(28, 30) // CD1-2HD1
			add(28, 31) // CD1-4HD1
			add(17, 32) // CA-C
			add(32, 33) // C-O
		}
		chain.add(Residue("GLU", 0 .. 14))
		chain.add(Residue("ILE", 15 .. 33))
	}

	// open a window
	val win = Window(
		width = 800,
		height = 600,
		title = "MolScope"
	).autoClose()

	// TODO: choose window menu features

	// prepare a slide for the molecule
	win.slides.add(Slide(mol.name).apply {
		lock { s ->
			s.views.add(SpaceFilling(mol))
			s.camera.lookAtEverything()
		}
	})

	// prepare a slide for the dipeptide
	win.slides.add(Slide(dipeptide.name).apply {
		lock { s ->
			s.views.add(BallAndStick(dipeptide))
			s.camera.lookAtEverything()
		}
	})

	// TODO: choose slide menu features

	win.waitForClose()

} // end of scope here cleans up all autoClose() resources

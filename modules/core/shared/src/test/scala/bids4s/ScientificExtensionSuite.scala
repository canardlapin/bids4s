package bids4s

class ScientificExtensionSuite extends munit.FunSuite:
  test("compound GIFTI extensions preserve suffix and semantic roundtrip"):
    Vector("surf", "shape", "label", "func", "rgba", "coord", "tensor").foreach { format =>
      val filename = s"tpl-fsLR_hemi-L_den-32k_pial.$format.gii"
      val name = BidsName.parseGeneric(filename).toOption.get
      assertEquals(name.kind, "pial")
      assertEquals(name.extension, s"$format.gii")
      assertEquals(BidsName.parseGeneric(name.fileName), Right(name))
    }

  test("MAT affine transform retains source, mode and transform suffix"):
    val filename = "tpl-MNI152NLin2009cAsym_from-OASISTRT20_mode-image_xfm.mat"
    val name = BidsName.parseGeneric(filename).toOption.get
    assertEquals(name.kind, "xfm")
    assertEquals(name.extension, "mat")
    assertEquals(name.entities.get(EntityKey.From), Some("OASISTRT20"))
    assertEquals(name.entities.get(EntityKey.Mode), Some("image"))
    assertEquals(BidsName.parseGeneric(name.fileName), Right(name))

  test("existing compound and generic GIFTI forms retain their boundaries"):
    Vector("nii.gz", "tsv.gz", "lv.h5", "gii").foreach { extension =>
      val name = BidsName.parseGeneric(s"sub-01_test.$extension").toOption.get
      assertEquals(name.kind, "test")
      assertEquals(name.extension, extension)
    }
    assert(BidsName.parseGeneric("sub-01_test.unknown").isLeft)
    assert(BidsName.parseGeneric("sub-01_test").isLeft)

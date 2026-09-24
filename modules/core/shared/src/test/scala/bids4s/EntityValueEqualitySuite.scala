package bids4s

class EntityValueEqualitySuite extends munit.FunSuite:
  test("numeric entity identity ignores padding without integer overflow"):
    for key <- Vector(EntityKey.Run, EntityKey.Echo, EntityKey.Custom("chunk", "chunk")) do
      assert(key.equivalentValue("01", "1"))
      assert(key.equivalentValue("0", "000"))
      assert(!key.equivalentValue("01", "2"))
      val huge = "9" * 100
      assert(key.equivalentValue("000" + huge, huge))
      assert(!key.equivalentValue(huge, huge.dropRight(1)))

  test("labels and malformed numeric strings are never normalized"):
    for key <- Vector(EntityKey.Subject, EntityKey.Session, EntityKey.Resolution, EntityKey.Custom("custom", "custom")) do
      assert(!key.equivalentValue("01", "1"))
    for pair <- Vector("+1" -> "1", " 1" -> "1", "1.0" -> "1", "01a" -> "1a", "" -> "0") do
      assert(!EntityKey.Run.equivalentValue(pair._1, pair._2))
    assert(EntityKey.Run.equivalentValue("legacyA", "legacyA"))

  test("semantic comparison retains raw entity rendering"):
    val original = BidsEntities.of(EntityKey.Subject -> "01", EntityKey.Run -> "001").toOption.get
    assert(EntityKey.Run.equivalentValue(original(EntityKey.Run), "1"))
    assertEquals(original.renderParts, Vector("sub-01", "run-001"))

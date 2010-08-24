package com.intellij.spellchecker.compress;

import junit.framework.TestCase;

public class UnitBitSetTests extends TestCase {

    public void testUnitValue() {
        int bitsPerUnit = 256;
        for (int i = 0; i < bitsPerUnit-1; i++) {
            UnitBitSet bs = UnitBitSet.create(bitsPerUnit);
            bs.setUnitValue(0, i);
            assertEquals(i, bs.getUnitValue(0));
            assertEquals(0, bs.getUnitValue(1));
        }
    }

     public void testCreateFromBitSet() {
        UnitBitSet bs1 = UnitBitSet.create(1,5,10);
        UnitBitSet bs2 = UnitBitSet.create(bs1,7);
        assertEquals(bs2,bs1); 
    }

     public void testMoveLeft() {
        UnitBitSet bs1 = UnitBitSet.create(3,true, 1,3,5,10);
        bs1.moveLeft(2);
        assertEquals(UnitBitSet.create(3,true,4),bs1); 
    }

     public void testMoveLeft2() {
        UnitBitSet bs1 = UnitBitSet.create(10,true, 1,3,5,10);
        bs1.moveLeft(2);
        assertEquals(UnitBitSet.create(10,true),bs1); 
    }

     public void testMoveRight() {
        UnitBitSet bs1 = UnitBitSet.create(3,true, 1,3,5,10);
        bs1.moveRight(2);
        assertEquals(UnitBitSet.create(3,true,7,9,11,16),bs1); 
    }



}

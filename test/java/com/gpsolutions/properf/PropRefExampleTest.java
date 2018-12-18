package com.gpsolutions.properf;

import org.junit.Assert;
import org.junit.Test;

public class PropRefExampleTest {
    public static class SomeEntity {
        private String name;
        private long id;
        private OtherEntity relatedEntity;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public OtherEntity getRelatedEntity() {
            return relatedEntity;
        }

        public void setRelatedEntity(OtherEntity relatedEntity) {
            this.relatedEntity = relatedEntity;
        }
    }
    public static class OtherEntity {
        private String name;
        private long id;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void simpleTest() {
        Assert.assertEquals("name", PropName.$(SomeEntity::getName));
        Assert.assertEquals("id", PropName.$(SomeEntity::getId));
    }

    @Test
    public void moreSofisticatedTest() {
        Assert.assertEquals("relatedEntity.name", PropRef.$(SomeEntity::getRelatedEntity).$(OtherEntity::getName).toString());
        SomeEntity sEntity = Instantiate.$(new SomeEntity(), € -> {
            €.setId(10);
            €.setName("Hello");
            €.setRelatedEntity(Instantiate.$(new OtherEntity(), €€ -> {
                €€.setId(100);
                €€.setName("World");
            }));
        });
        PropRef.PropertyReference<SomeEntity, String> sEntityName = PropRef.$(SomeEntity::getName);
        PropRef.PropertyReference<SomeEntity, String> oEntityName = PropRef.$(SomeEntity::getRelatedEntity).$(OtherEntity::getName);
        PropRef.PropertyReference<SomeEntity, OtherEntity> oEntity = PropRef.$(SomeEntity::getRelatedEntity);
        Assert.assertEquals("name", sEntityName.toString());
        Assert.assertEquals("relatedEntity.name", oEntityName.toString());
        Assert.assertEquals("Hello", sEntityName.getValueOf(sEntity));
        Assert.assertEquals("World", oEntityName.getValueOf(sEntity));
        Assert.assertEquals(100, oEntity.getValueOf(sEntity).getId());
    }
}

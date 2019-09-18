package impl;

import api.ObjectFactory;
import api.PooledObject;

import java.util.Random;

public class StudentFactory implements ObjectFactory<Student>{

    @Override
    public PooledObject<Student> createObject() throws Exception {
        return new PooledObjectImpl<>(new Student(new Random().nextInt(),"ddd"));
    }

    @Override
    public void activateObject(PooledObject<Student> p) throws Exception {

    }

    @Override
    public boolean validateObject(PooledObject<Student> p) {
        return new Random().nextBoolean();
    }

    @Override
    public void passivateObject(PooledObject<Student> p) throws Exception {

    }

    @Override
    public void destroyObject(PooledObject<Student> p) throws Exception {

    }
}

package com.redhat.ceylon.test.eclipse.plugin.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TestElement implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum State {

        UNDEFINED,
        RUNNING,
        SUCCESS,
        FAILURE,
        ERROR,
        SKIPPED_OR_ABORTED;

        public boolean isFinished() {
            return this == SUCCESS || this == FAILURE || this == ERROR || this == SKIPPED_OR_ABORTED;
        }

        public boolean isFailureOrError() {
            return this == FAILURE || this == ERROR;
        }
        
        public boolean canShowStackTrace() {
            return this == FAILURE || this == ERROR || this == State.SKIPPED_OR_ABORTED;
        }

    }

    private String shortName;
    private String packageName;
    private String qualifiedName;
    private String variant;
    private Long variantIndex;
    private State state;
    private String exception;
    private String expectedValue;
    private String actualValue;
    private long elapsedTimeInMilis;
    private List<TestElement> children = new ArrayList<TestElement>();

    public String getShortName() {
        return shortName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;

        int packageSeparatorIndex = qualifiedName.indexOf("::");
        if (packageSeparatorIndex != -1) {
            String tmp = qualifiedName.substring(packageSeparatorIndex + 2);
            int memberSeparatorIndex = tmp.lastIndexOf(".");
            if (memberSeparatorIndex != -1) {
                shortName = tmp.substring(memberSeparatorIndex + 1);
            } else {
                shortName = tmp;
            }
            packageName = qualifiedName.substring(0, packageSeparatorIndex);
        } else {
            shortName = qualifiedName;
            packageName = "";
        }
    }
    
    public String getVariant() {
        return variant;
    }
    
    public void setVariant(String variant) {
        this.variant = variant;
    }
    
    public Long getVariantIndex() {
        return variantIndex;
    }
    
    public void setVariantIndex(Long variantIndex) {
        this.variantIndex = variantIndex;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public long getElapsedTimeInMilis() {
        return elapsedTimeInMilis;
    }

    public void setElapsedTimeInMilis(long elapsedTimeInMilis) {
        this.elapsedTimeInMilis = elapsedTimeInMilis;
    }
    
    public List<TestElement> getChildren() {
        return Collections.unmodifiableList(children);
    }
    
    public void setChildren(List<TestElement> children) {
        this.children = new ArrayList<TestElement>(children);
    }
    
    public void addChild(TestElement testElement) {
        children.add(testElement);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if(obj instanceof TestElement) {
            TestElement e2 = (TestElement) obj;
            return Objects.equals(qualifiedName, e2.qualifiedName) &&
                    Objects.equals(variantIndex, e2.variantIndex);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return qualifiedName != null ? qualifiedName.hashCode() : 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TestElement");
        builder.append("[");
        builder.append("name=").append(qualifiedName).append(", ");
        builder.append("state=").append(state);
        if(variant != null) {
            builder.append(", variant=").append(variant);
        }
        builder.append("]");
        return builder.toString();
    }


}
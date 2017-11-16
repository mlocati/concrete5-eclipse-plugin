package org.concrete5.core.factory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.Serializable;

public class FactoryMethod implements Serializable {
	private static final long serialVersionUID = 1L;
	public final static int TYPE_INSTANCEMETHOD = 1;
	public final String className;
	public final String methodName;
	public final int discrimintatorIndex;
	public final int type;
	public boolean generateClasses;
	public final Map<String, String> aliases;

	public FactoryMethod(String className, String methodName, int discrimintatorIndex, int type) {
		this.className = className;
		this.methodName = methodName;
		this.discrimintatorIndex = discrimintatorIndex;
		this.type = type;
		this.generateClasses = false;
		this.aliases = new ConcurrentHashMap<String, String>();
	}
}

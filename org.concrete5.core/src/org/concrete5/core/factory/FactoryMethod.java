package org.concrete5.core.factory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FactoryMethod implements Serializable {
	private static final long serialVersionUID = 1L;
	public final static int TYPE_INSTANCEMETHOD = 0x001;
	public String definerResourcePath;
	public final String className;
	public final String methodName;
	public final int discrimintatorIndex;
	public final int type;
	public boolean generateClasses;
	public final Map<String, String> aliases;

	public FactoryMethod(String definerResourcePath, String className, String methodName, int discrimintatorIndex,
			int type) {
		this.definerResourcePath = definerResourcePath;
		this.className = className;
		this.methodName = methodName;
		this.discrimintatorIndex = discrimintatorIndex;
		this.type = type;
		this.generateClasses = false;
		this.aliases = new HashMap<String, String>();
	}
}

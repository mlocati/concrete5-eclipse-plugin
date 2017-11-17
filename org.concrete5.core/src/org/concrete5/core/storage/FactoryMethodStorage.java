package org.concrete5.core.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.concrete5.core.Common;
import org.concrete5.core.Concrete5CorePlugin;
import org.concrete5.core.factory.FactoryMethod;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

public class FactoryMethodStorage {

	private final static FactoryMethod[] emptyList = new FactoryMethod[0];
	private IProject project = null;
	private FactoryMethod[] factoryMethods;
	private Boolean concrete5NatureFlag;

	public FactoryMethodStorage(IProject project) {
		this.project = project;
		this.reset();
	}

	public void dispose() {
		this.reset();
		this.project = null;
	}

	public void projectChanged() {
		this.reset();
	}

	public FactoryMethod[] getAllFactoryMethods() {
		if (this.factoryMethods != null) {
			return this.factoryMethods;
		}
		if (this.hasConcrete5Nature() == false) {
			this.factoryMethods = FactoryMethodStorage.emptyList;
		} else {
			ObjectInputStream stream = this.getStorageInput();
			if (stream == null) {
				this.factoryMethods = FactoryMethodStorage.emptyList;
			} else {
				try {
					this.factoryMethods = (FactoryMethod[]) stream.readObject();
				} catch (ClassNotFoundException | IOException e) {
					this.factoryMethods = FactoryMethodStorage.emptyList;
				} finally {
					try {
						stream.close();
					} catch (Throwable foo) {
					}
				}
				if (this.factoryMethods == null) {
					this.factoryMethods = FactoryMethodStorage.emptyList;
				}
			}
		}

		return this.factoryMethods;
	}

	public void addFactoryMethod(FactoryMethod factoryMethod) {
		if (this.hasConcrete5Nature() == false) {
			return;
		}
		FactoryMethod[] currentList = this.getAllFactoryMethods();
		FactoryMethod[] newList = Arrays.copyOf(currentList, currentList.length + 1);
		newList[currentList.length] = factoryMethod;
		this.setFactoryMethods(newList);
	}

	private void setFactoryMethods(FactoryMethod[] newList) {
		ObjectOutputStream stream = null;
		try {
			if (newList.length == 0) {
				File file = this.getStorageFile();
				if (file.isFile()) {
					file.delete();
				}
			} else {
				stream = this.getStorageOutput();
				stream.writeObject(newList);
			}
		} catch (Throwable e) {
			try {
				Concrete5CorePlugin plugin = Concrete5CorePlugin.getDefault();
				Platform.getLog(plugin.getBundle())
						.log(new Status(Status.ERROR, Concrete5CorePlugin.PLUGIN_ID, e.getMessage(), e));
			} catch (Throwable foo) {
			}
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (Throwable foo) {
				}
			}
		}
		this.factoryMethods = newList;
	}

	public void resetForPath(String path)
	{
		if (path == null) {
			return;
		}
		FactoryMethod[] currentList = this.getAllFactoryMethods();
		if (currentList.length == 0) {
			return;
		}
		List<FactoryMethod> newList = new ArrayList<FactoryMethod>(currentList.length);
		for (FactoryMethod factoryMethod : currentList) {
			if (!factoryMethod.definerResourcePath.equals(path)) {
				newList.add(factoryMethod);
			}
		}
		int newSize = newList.size();
		if (newSize == currentList.length) {
			return;
		}
		FactoryMethod[] newArray = new FactoryMethod[newSize];  
		this.setFactoryMethods(newList.toArray(newArray));
	}

	private boolean hasConcrete5Nature() {
		if (this.concrete5NatureFlag == null) {
			this.concrete5NatureFlag = new Boolean(Common.hasConcrete5Nature(this.project));
		}
		return this.concrete5NatureFlag.booleanValue();
	}

	private void reset() {
		this.factoryMethods = null;
		this.concrete5NatureFlag = null;
	}

	private File getStorageFile() {
		Concrete5CorePlugin plugin = Concrete5CorePlugin.getDefault();
		if (plugin == null) {
			return null;
		}
		IPath stateLocation = plugin.getStateLocation();
		if (stateLocation == null) {
			return null;
		}
		File file = stateLocation.append(this.project.getFullPath()).append("factoryMethods").toFile();
		return file;
	}

	private ObjectInputStream getStorageInput() {
		File file = this.getStorageFile();
		if (file == null || !file.isFile()) {
			return null;
		}
		FileInputStream inputStream;
		try {
			inputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			return null;
		}
		try {
			return new ObjectInputStream(inputStream);
		} catch (IOException e) {
			try {
				inputStream.close();
			} catch (Throwable foo) {
			}
			return null;
		}
	}

	public List<FactoryMethod> getFactoryMethodsByMethodName(String methodName, int typeFlags) {
		FactoryMethod[] factoryMethods = this.getAllFactoryMethods();
		List<FactoryMethod> result = new ArrayList<FactoryMethod>(factoryMethods.length);
		for (FactoryMethod factoryMethod : factoryMethods) {
			if ((factoryMethod.type & typeFlags) != 0) {
				if (methodName.equalsIgnoreCase(factoryMethod.methodName)) {
					result.add(factoryMethod);
				}
			}
		}
		return result;
	}

	private ObjectOutputStream getStorageOutput() throws IOException {
		File file = this.getStorageFile();
		if (file == null) {
			return null;
		}
		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}
		if (!file.isFile()) {
			file.createNewFile();
		}
		FileOutputStream outputStream = new FileOutputStream(file, false);
		try {
			return new ObjectOutputStream(outputStream);
		} catch (IOException x) {
			try {
				outputStream.close();
			} catch (Throwable foo) {
			}
			throw x;
		}
	}
}

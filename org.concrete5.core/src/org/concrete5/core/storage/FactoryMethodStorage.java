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

import org.concrete5.core.Concrete5CorePlugin;
import org.concrete5.core.builder.ProjectData;
import org.concrete5.core.factory.FactoryMethod;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

public class FactoryMethodStorage {

	private final static FactoryMethod[] emptyList = new FactoryMethod[0];
	private ProjectData projectData = null;
	private FactoryMethod[] factoryMethods;

	public FactoryMethodStorage(ProjectData projectData) {
		this.projectData = projectData;
	}

	public FactoryMethod[] getAllFactoryMethods() {
		if (this.factoryMethods != null) {
			return this.factoryMethods;
		}
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

		return this.factoryMethods;
	}

	public void addFactoryMethod(FactoryMethod factoryMethod) {
		FactoryMethod[] currentList = this.getAllFactoryMethods();
		FactoryMethod[] newList = Arrays.copyOf(currentList, currentList.length + 1);
		newList[currentList.length] = factoryMethod;
		this.setFactoryMethods(newList);
	}

	public void removeAllFactoryMethods() {
		this.setFactoryMethods(new FactoryMethod[0]);
		this.factoryMethods = null;
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

	public void resetForPath(String path) {
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

	public void renameForPath(String oldFilename, String newFilename) {
		FactoryMethod[] currentList = this.getAllFactoryMethods();
		if (currentList.length == 0) {
			return;
		}
		boolean changed = false;
		for (int i = currentList.length - 1; i >= 0; i--) {
			if (currentList[i].definerResourcePath.equals(oldFilename)) {
				changed = true;
				currentList[i].definerResourcePath = newFilename;
			}
		}
		if (!changed) {
			return;
		}
		this.setFactoryMethods(currentList);

	}

	private File getStorageFile() {
		File file = this.projectData.getDataPath().append("factoryMethods").toFile();
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

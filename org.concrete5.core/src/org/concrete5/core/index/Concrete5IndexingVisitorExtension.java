package org.concrete5.core.index;

import java.util.Collection;
import java.util.List;

import org.concrete5.core.builder.ProjectData;
import org.concrete5.core.builder.ProjectDataFactory;
import org.concrete5.core.factory.FactoryMethod;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.declarations.TypeDeclaration;
import org.eclipse.dltk.ast.expressions.CallArgumentsList;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.SimpleReference;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.index2.IIndexingRequestor.ReferenceInfo;
import org.eclipse.php.core.compiler.ast.nodes.ArrayCreation;
import org.eclipse.php.core.compiler.ast.nodes.ArrayElement;
import org.eclipse.php.core.compiler.ast.nodes.FullyQualifiedReference;
import org.eclipse.php.core.compiler.ast.nodes.NamespaceDeclaration;
import org.eclipse.php.core.compiler.ast.nodes.NamespaceReference;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.core.compiler.ast.nodes.Scalar;
import org.eclipse.php.core.compiler.ast.nodes.StaticConstantAccess;
import org.eclipse.php.core.compiler.ast.nodes.StaticMethodInvocation;
import org.eclipse.php.core.index.PHPIndexingVisitorExtension;
import org.eclipse.php.internal.core.compiler.ast.parser.ASTUtils;

@SuppressWarnings("restriction")
public class Concrete5IndexingVisitorExtension extends PHPIndexingVisitorExtension {
	private final static String NAMESPACE_NAME = "PHPSTORM_META"; //$NON-NLS-1$
	private final static String OVERRIDE_FUNCTION = "override"; //$NON-NLS-1$
	private final static String MAP_FUNCTION = "map"; //$NON-NLS-1$

	private SourceModuleData sourceModuleData = null;
	private boolean isInMetaNamespace = false;

	private class SourceModuleData {
		public final ProjectData projectData;
		public final String sourceModulePath;

		public SourceModuleData(IProject project, String sourceModulePath) {
			this.projectData = ProjectDataFactory.get(project);
			this.sourceModulePath = sourceModulePath;
		}
	}

	public void setSourceModule(ISourceModule module) {
		super.setSourceModule(module);
		String sourceModulePath = null;
		this.sourceModuleData = null;
		if (module != null) {
			IScriptProject scriptProject = module.getScriptProject();
			if (scriptProject != null) {
				IProject project = scriptProject.getProject();
				if (project != null) {
					IPath path = module.getPath();
					if (path != null) {
						sourceModulePath = path.toPortableString();
						if (sourceModulePath != null && sourceModulePath.isEmpty()) {
							sourceModulePath = null;
						}
					}
					this.sourceModuleData = new SourceModuleData(project, sourceModulePath);
					if (sourceModulePath != null) {
						this.sourceModuleData.projectData.getFactoryMethodStorage().resetForPath(sourceModulePath);
					}
				}
			}
		}
	}

	public boolean visit(TypeDeclaration type) throws Exception {
		if (type instanceof NamespaceDeclaration) {
			String namespaceName = type.getName();
			if (namespaceName != null && namespaceName.equals(NAMESPACE_NAME) && this.sourceModuleData != null
					&& this.sourceModuleData.sourceModulePath != null
					&& this.sourceModuleData.projectData.hasConcrete5Nature()) {
				this.isInMetaNamespace = true;
			}
		}
		return true;
	}

	public boolean endvisit(TypeDeclaration type) throws Exception {
		if (this.isInMetaNamespace && type instanceof NamespaceDeclaration) {
			this.isInMetaNamespace = false;
		}
		return true;
	}

	public void modifyReference(ASTNode node, ReferenceInfo info) {
		if (!this.isInMetaNamespace || !(node instanceof PHPCallExpression)) {
			return;
		}
		FactoryMethod factoryMethod = this.extractFactoryMethod((PHPCallExpression) node);
		if (factoryMethod == null) {
			return;
		}
		this.sourceModuleData.projectData.getFactoryMethodStorage().addFactoryMethod(factoryMethod);
	}

	private FactoryMethod extractFactoryMethod(PHPCallExpression call) {
		if (call.getReceiver() != null || !OVERRIDE_FUNCTION.equalsIgnoreCase(call.getName())) {
			return null;
		}
		CallArgumentsList argsList = call.getArgs();
		List<ASTNode> args = argsList == null ? null : argsList.getChilds();
		int numArgs = args == null ? 0 : args.size();

		if (numArgs == 2) {
			if (args.get(0) instanceof StaticMethodInvocation && args.get(1) instanceof PHPCallExpression) {
				return this.extractFactoryMethod((StaticMethodInvocation) args.get(0), (PHPCallExpression) args.get(1));
			}
			if (args.get(0) instanceof PHPCallExpression && args.get(1) instanceof PHPCallExpression) {
				return this.extractFactoryMethod((PHPCallExpression) args.get(0), (PHPCallExpression) args.get(1));
			}
		}
		return null;
	}

	private FactoryMethod extractFactoryMethod(PHPCallExpression factory, PHPCallExpression map) {
		SimpleReference name = factory.getCallName();
		if (!(name instanceof FullyQualifiedReference)) {
			return null;
		}
		return this.extractFactoryMethod(factory, "", name.getName(), map);
	}

	private FactoryMethod extractFactoryMethod(StaticMethodInvocation factory, PHPCallExpression map) {
		ASTNode receiver = factory.getReceiver();
		if (!(receiver instanceof FullyQualifiedReference)) {
			return null;
		}
		FullyQualifiedReference fqr = (FullyQualifiedReference) receiver;
		String factoryClass = fqr.getFullyQualifiedName();
		if (factoryClass == null || factoryClass.length() < 2
				|| factoryClass.charAt(0) != NamespaceReference.NAMESPACE_SEPARATOR) {
			return null;
		}
		factoryClass = factoryClass.substring(1);
		SimpleReference factoryMethodSR = factory.getCallName();
		String factoryMethod = factoryMethodSR == null ? null : factoryMethodSR.getName();
		if (factoryMethod == null || factoryMethod.isEmpty()) {
			return null;
		}
		return this.extractFactoryMethod(factory, factoryClass, factoryMethod, map);
	}

	private FactoryMethod extractFactoryMethod(PHPCallExpression factory, String factoryClass, String factoryMethod, PHPCallExpression map)
	{
		CallArgumentsList argsList = factory.getArgs();
		if (argsList == null) {
			return null;
		}
		List<ASTNode> args = argsList.getChilds();
		if (args == null || args.size() != 1 || !(args.get(0) instanceof Scalar)) {
			return null;
		}
		Scalar discriminatorArg = (Scalar) args.get(0);
		if (discriminatorArg.getScalarType() != Scalar.TYPE_INT) {
			return null;
		}
		int discrimintatorIndex;
		try {
			discrimintatorIndex = Integer.parseInt(discriminatorArg.getValue());
		} catch (NumberFormatException x) {
			discrimintatorIndex = -1;
		}
		if (discrimintatorIndex < 0) {
			return null;
		}
		SourceModuleData sourceModuleData = this.sourceModuleData;
		if (sourceModuleData.sourceModulePath == null) {
			return null;
		}
		return this.extractMappings(new FactoryMethod(sourceModuleData.sourceModulePath, factoryClass, factoryMethod,
				discrimintatorIndex, FactoryMethod.TYPE_INSTANCEMETHOD), map);
	}

	private FactoryMethod extractMappings(FactoryMethod factoryMethod, PHPCallExpression map) {
		if (map.getReceiver() != null || !MAP_FUNCTION.equalsIgnoreCase(map.getName())) {
			return null;
		}
		CallArgumentsList argsList = map.getArgs();
		if (argsList == null) {
			return null;
		}
		List<ASTNode> args = argsList.getChilds();
		if (args == null || args.size() != 1 || !(args.get(0) instanceof ArrayCreation)) {
			return null;
		}
		Collection<ArrayElement> elements = ((ArrayCreation) args.get(0)).getElements();
		if (elements == null || elements.isEmpty()) {
			return null;
		}
		boolean somethingFound = false;
		for (ArrayElement element : elements) {
			if (element == null) {
				continue;
			}
			Expression elementKey = element.getKey();
			if (!(elementKey instanceof Scalar)) {
				continue;
			}
			Scalar scalarKey = (Scalar) elementKey;
			if (scalarKey.getScalarType() != Scalar.TYPE_STRING) {
				continue;
			}
			String key = ASTUtils.stripQuotes(scalarKey.getValue());
			Expression elementValue = element.getValue();
			if (elementValue instanceof Scalar) {
				Scalar scalarValue = (Scalar) elementValue;
				if (scalarValue.getScalarType() != Scalar.TYPE_STRING) {
					continue;
				}
				String value = ASTUtils.stripQuotes(scalarValue.getValue());
				if (!value.isEmpty()) {
					if (key.isEmpty()) {
						factoryMethod.fallbackAlias = value;
					} else {
						factoryMethod.aliases.put(key, value);
					}
					somethingFound = true;
				}
			} else if (elementValue instanceof StaticConstantAccess) {
				StaticConstantAccess scaValue = (StaticConstantAccess) elementValue;
				Expression dispatcher = scaValue.getDispatcher();
				if (dispatcher instanceof FullyQualifiedReference) {
					String constant = scaValue.getConstant() == null ? null : scaValue.getConstant().getName();
					if (constant != null && "class".equalsIgnoreCase(constant)) { //$NON-NLS-1$
						String className = ((FullyQualifiedReference) dispatcher).getFullyQualifiedName();
						if (className != null && className.length() > 1
								&& className.charAt(0) == NamespaceReference.NAMESPACE_SEPARATOR) {
							if (key.isEmpty()) {
								factoryMethod.fallbackAlias = className.substring(1);
							} else {
								factoryMethod.aliases.put(key, className.substring(1));
							}
							somethingFound = true;
						}
					}
				}
			}
		}

		return somethingFound ? factoryMethod : null;
	}
}

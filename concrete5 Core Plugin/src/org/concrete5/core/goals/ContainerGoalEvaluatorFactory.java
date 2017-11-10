package org.concrete5.core.goals;

import java.util.List;

import org.concrete5.core.Common;
import org.concrete5.core.goals.evaluator.PHPClassEvaluator;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.expressions.CallArgumentsList;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.SimpleReference;
import org.eclipse.dltk.ti.IGoalEvaluatorFactory;
import org.eclipse.dltk.ti.goals.ExpressionTypeGoal;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.php.core.compiler.ast.nodes.FullyQualifiedReference;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.core.compiler.ast.nodes.Scalar;
import org.eclipse.php.core.compiler.ast.nodes.StaticConstantAccess;
import org.eclipse.php.core.compiler.ast.nodes.StaticMethodInvocation;
import org.eclipse.php.internal.core.compiler.ast.parser.ASTUtils;

@SuppressWarnings("restriction")
public class ContainerGoalEvaluatorFactory implements IGoalEvaluatorFactory {

	/**
	 * Factory method flags: not a factory method.
	 */
	private static final int FACTORYMETHOD_NONE = 0x0000;

	/**
	 * Factory method flags: factory method that may return a class instance.
	 */
	private static final int FACTORYMETHOD_CLASSES = 0x0001;

	/**
	 * Factory method flags: factory method that may resolve classes.
	 */
	private static final int FACTORYMETHOD_ALIASES = 0x0002;

	@Override
	public GoalEvaluator createEvaluator(IGoal goal) {
		if (this.checkNature(goal) == false) {
			return null;
		}
		int factoryMethodFlags = this.checkFactoryMethod(goal);
		if (factoryMethodFlags == FACTORYMETHOD_NONE) {
			return null;
		}
		String resultingClassName = this.parseFactoryMethodArgument(goal, factoryMethodFlags);
		if (resultingClassName == null) {
			return null;
		}
		return new PHPClassEvaluator(goal, resultingClassName);
	}

	/**
	 * Does the goal is associated to a concrete5 project?
	 *
	 * @param goal
	 *            The goal to check.
	 * @return true if the goal is associated to a concret5 project, false
	 *         otherwise.
	 */
	private boolean checkNature(IGoal goal) {
		return goal != null && Common.hasConcrete5Nature(goal.getContext());
	}

	/**
	 * Check if a goal is a factory method call, and if so determine what it can be
	 * evaluate.
	 *
	 * @param goal
	 *            The goal to check.
	 * @return FACTORYMETHOD_NONE if the goal is not associated to a factory method
	 *         call, other flags otherwise.
	 */
	private int checkFactoryMethod(IGoal goal) {
		if (!(goal instanceof ExpressionTypeGoal)) {
			return FACTORYMETHOD_NONE;
		}
		ASTNode expression = ((ExpressionTypeGoal) goal).getExpression();
		if (!(expression instanceof PHPCallExpression)) {
			return FACTORYMETHOD_NONE;
		}
		PHPCallExpression call = (PHPCallExpression) expression;
		ASTNode receiver = call.getReceiver();
		if (call instanceof StaticMethodInvocation) {
			if (!(receiver instanceof SimpleReference)
					&& !this.isFactoryFacadeClass(((SimpleReference) receiver).getName())) {
				return FACTORYMETHOD_NONE;
			}
		} else {
			// @todo: check if $receiver is a variable containing a factory instance
			// For now let's just rely on the method names.
		}
		String methodName = (call.getCallName() == null) ? "" : call.getCallName().getName();
		int flags;
		if (methodName.equalsIgnoreCase("build")) {
			flags = FACTORYMETHOD_CLASSES;
		} else if (methodName.equalsIgnoreCase("make")) {
			flags = FACTORYMETHOD_CLASSES | FACTORYMETHOD_ALIASES;
		} else {
			flags = FACTORYMETHOD_NONE;
		}

		return flags;
	}

	/**
	 * Check if a string is the name of a factory facade.
	 *
	 * @param facadeName
	 *            The string to be checked.
	 * @return Return true if facadeName contains a facade name, false otherwise.
	 */
	private boolean isFactoryFacadeClass(String facadeName) {
		// @todo Check namespace and "use" statements
		return facadeName != null && ("Core".equalsIgnoreCase(facadeName) || "\\Core".equalsIgnoreCase(facadeName));
	}

	/**
	 * Parse the first argument of the call and extract the resulting class name.
	 * 
	 * @param goal
	 *            The goal to be checked
	 * @param factoryMethodFlags
	 * @return NULL if the first argument is not available or if does not resolve to
	 *         a class name
	 */
	private String parseFactoryMethodArgument(IGoal goal, int factoryMethodFlags) {
		if (!(goal instanceof ExpressionTypeGoal)) {
			return null;
		}
		ASTNode expression = ((ExpressionTypeGoal) goal).getExpression();
		if (!(expression instanceof PHPCallExpression)) {
			return null;
		}
		CallArgumentsList argumentsContainer = ((PHPCallExpression) expression).getArgs();
		List<ASTNode> arguments = argumentsContainer == null ? null : argumentsContainer.getChilds();
		if (arguments == null || arguments.size() < 1) {
			return null;
		}
		ASTNode firstArgument = arguments.get(0);
		if (firstArgument instanceof StaticConstantAccess) {
			return this.parseFactoryMethodArgument((StaticConstantAccess) firstArgument, factoryMethodFlags);
		}
		if (firstArgument instanceof Scalar) {
			return this.parseFactoryMethodArgument((Scalar) firstArgument, factoryMethodFlags);
		}
		return null;
	}

	/**
	 * Extract the class name from an argument like "ClassName::class"
	 * 
	 * @param argument
	 *            The argument to be parsed
	 * @param factoryMethodFlags
	 * @return NULL if the argument is not a ..::class call, the class name
	 *         otherwise
	 */
	private String parseFactoryMethodArgument(StaticConstantAccess argument, int factoryMethodFlags) {
		if ("class".equalsIgnoreCase(argument.getConstant().getName())
				&& (factoryMethodFlags & FACTORYMETHOD_CLASSES) != 0) {
			Expression dispatcher = argument.getDispatcher();
			if (dispatcher instanceof FullyQualifiedReference) {
				FullyQualifiedReference fqr = (FullyQualifiedReference) dispatcher;
				String fqn = fqr.getFullyQualifiedName();
				if (fqn != null && fqn.length() > 0) {
					// @todo check namespace and "use" statements to resolve the fully-qualified
					// class name
					return fqn;
				}
			}
		}
		// @todo Check if the argument is referencing a constant containing a string
		return null;
	}

	/**
	 * Extract the string contained in an argument and resolve it to a class name
	 * 
	 * @param argument
	 *            The argument to be parsed
	 * @param factoryMethodFlags
	 * @return NULL if the argument can't be resolved to a class name
	 */
	private String parseFactoryMethodArgument(Scalar argument, int factoryMethodFlags) {
		switch (argument.getScalarType()) {
		case Scalar.TYPE_STRING:
			String str = ASTUtils.stripQuotes(argument.getValue());
			if (str != null && str.length() > 0) {
				if ((factoryMethodFlags & FACTORYMETHOD_ALIASES) != 0) {
					String resolved = this.resolveAlias(str);
					if (resolved != null) {
						return resolved;
					}
				}
				if ((factoryMethodFlags & FACTORYMETHOD_CLASSES) != 0) {
					// @todo Check if str is the name of a class
					// for now let's simply check if its first character is upper case
					if (Character.isUpperCase(str.charAt(0))) {
						return str;
					}
					if (str.length() > 1 && str.charAt(0) == '\\') {
						if (Character.isUpperCase(str.charAt(1))) {
							return str;
						}
						if (str.length() > 2 && str.charAt(1) == '\\' && Character.isUpperCase(str.charAt(2))) {
							return str;
						}
					}
				}
			}
			break;
		}
		return null;
	}

	/**
	 * Check if .phpstorm.meta.php defines an alias
	 * 
	 * @param aliasName
	 * @return NULL if there's no alias with the specified name
	 */
	private String resolveAlias(String aliasName) {
		// @todo Check the contents of .phpstorm.meta.php
		switch (aliasName) {
		case "cache":
			return "\\Concrete\\Core\\Cache\\Level\\ObjectCache";
		case "cache/expensive":
			return "\\Concrete\\Core\\Cache\\Level\\ExpensiveCache";
		case "cache/overrides":
			return "\\Concrete\\Core\\Cache\\Level\\OverridesCache";
		case "cache/page":
			return "\\Concrete\\Core\\Cache\\Page\\FilePageCache";
		case "cache/request":
			return "\\Concrete\\Core\\Cache\\Level\\RequestCache";
		case "Concrete\\Core\\Captcha\\CaptchaInterface":
			return "\\Concrete\\Core\\Captcha\\SecurimageController";
		case "Concrete\\Core\\Captcha\\CaptchaWithPictureInterface":
			return "\\Concrete\\Core\\Captcha\\SecurimageController";
		case "Concrete\\Core\\Database\\EntityManagerConfigFactoryInterface":
			return "\\Concrete\\Core\\Database\\EntityManagerConfigFactory";
		case "Concrete\\Core\\Database\\EntityManagerFactoryInterface":
			return "\\Concrete\\Core\\Database\\EntityManagerFactory";
		case "Concrete\\Core\\Express\\Entry\\Formatter\\EntryFormatterInterface":
			return "\\Concrete\\Core\\Express\\Entry\\Formatter\\LabelFormatter";
		case "Concrete\\Core\\Express\\Formatter\\FormatterInterface":
			return "\\Concrete\\Core\\Express\\Formatter\\LabelFormatter";
		case "Concrete\\Core\\File\\StorageLocation\\StorageLocationInterface":
			return "\\Concrete\\Core\\Entity\\File\\StorageLocation\\StorageLocation";
		case "Concrete\\Core\\Http\\DispatcherInterface":
			return "\\Concrete\\Core\\Http\\DefaultDispatcher";
		case "Concrete\\Core\\Http\\Middleware\\StackInterface":
			return "\\Concrete\\Core\\Http\\Middleware\\MiddlewareStack";
		case "Concrete\\Core\\Http\\ResponseFactoryInterface":
			return "\\Concrete\\Core\\Http\\ResponseFactory";
		case "Concrete\\Core\\Http\\ServerInterface":
			return "\\Concrete\\Core\\Http\\DefaultServer";
		case "Concrete\\Core\\Localization\\Translator\\TranslatorAdapterFactoryInterface":
			return "\\Concrete\\Core\\Localization\\Translator\\Adapter\\Core\\TranslatorAdapterFactory";
		case "Concrete\\Core\\Routing\\RouterInterface":
			return "\\Concrete\\Core\\Routing\\Router";
		case "Concrete\\Core\\Search\\Index\\IndexManagerInterface":
			return "\\Concrete\\Core\\Search\\Index\\DefaultManager";
		case "Concrete\\Core\\Service\\Manager\\ManagerInterface":
			return "\\Concrete\\Core\\Service\\Manager\\ServiceManager";
		case "Concrete\\Core\\Session\\SessionFactoryInterface":
			return "\\Concrete\\Core\\Session\\SessionFactory";
		case "Concrete\\Core\\Session\\SessionValidatorInterface":
			return "\\Concrete\\Core\\Session\\SessionValidator";
		case "Concrete\\Core\\Site\\Resolver\\DriverInterface":
			return "\\Concrete\\Core\\Site\\Resolver\\StandardDriver";
		case "Concrete\\Core\\Statistics\\UsageTracker\\TrackerManagerInterface":
			return "\\Concrete\\Core\\Statistics\\UsageTracker\\AggregateTracker";
		case "Concrete\\Core\\Url\\Resolver\\Manager\\ResolverManagerInterface":
			return "\\Concrete\\Core\\Url\\Resolver\\Manager\\ResolverManager";
		case "Concrete\\Core\\User\\Avatar\\AvatarServiceInterface":
			return "\\Concrete\\Core\\User\\Avatar\\AvatarService";
		case "Concrete\\Core\\User\\RegistrationServiceInterface":
			return "\\Concrete\\Core\\User\\RegistrationService";
		case "Concrete\\Core\\User\\StatusServiceInterface":
			return "\\Concrete\\Core\\User\\StatusService";
		case "Concrete\\Core\\Validator\\ValidatorManagerInterface":
			return "\\Concrete\\Core\\Validator\\ValidatorManager";
		case "config":
			return "\\Concrete\\Core\\Config\\Repository\\Repository";
		case "config/database":
			return "\\Concrete\\Core\\Config\\Repository\\Repository";
		case "cookie":
			return "\\Concrete\\Core\\Cookie\\CookieJar";
		case "database":
			return "\\Concrete\\Core\\Database\\DatabaseManager";
		case "database/orm":
			return "\\Concrete\\Core\\Database\\DatabaseManagerORM";
		case "device/manager":
			return "\\Concrete\\Core\\Device\\DeviceManager";
		case "director":
			return "\\Symfony\\Component\\EventDispatcher\\EventDispatcher";
		case "Doctrine\\DBAL\\Connection":
			return "\\Concrete\\Core\\Database\\Connection\\Connection";
		case "Doctrine\\ORM\\EntityManagerInterface":
			return "\\Doctrine\\ORM\\EntityManager";
		case "editor":
			return "\\Concrete\\Core\\Editor\\CkeditorEditor";
		case "editor/image":
			return "\\Concrete\\Core\\ImageEditor\\ImageEditor";
		case "editor/image/core":
			return "\\Concrete\\Core\\ImageEditor\\ImageEditor";
		case "editor/image/extension/factory":
			return "\\Concrete\\Core\\ImageEditor\\ExtensionFactory";
		case "element":
			return "\\Concrete\\Core\\Filesystem\\ElementManager";
		case "environment":
			return "\\Concrete\\Core\\Foundation\\Environment";
		case "error":
			return "\\Concrete\\Core\\Error\\ErrorList\\ErrorList";
		case "express":
			return "\\Concrete\\Core\\Express\\ObjectManager";
		case "express/builder/association":
			return "\\Concrete\\Core\\Express\\ObjectAssociationBuilder";
		case "express/control/type/manager":
			return "\\Concrete\\Core\\Express\\Form\\Control\\Type\\Manager";
		case "form/express/entry_selector":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\ExpressEntrySelector";
		case "help":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help";
		case "help/block_type":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\BlockTypeManager";
		case "help/core":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\CoreManager";
		case "help/dashboard":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\DashboardManager";
		case "help/panel":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\PanelManager";
		case "helper/ajax":
			return "\\Concrete\\Core\\Http\\Service\\Ajax";
		case "helper/arrays":
			return "\\Concrete\\Core\\Utility\\Service\\Arrays";
		case "helper/concrete/asset_library":
			return "\\Concrete\\Core\\Application\\Service\\FileManager";
		case "helper/concrete/avatar":
			return "\\Concrete\\Core\\Legacy\\Avatar";
		case "helper/concrete/composer":
			return "\\Concrete\\Core\\Application\\Service\\Composer";
		case "helper/concrete/dashboard":
			return "\\Concrete\\Core\\Application\\Service\\Dashboard";
		case "helper/concrete/dashboard/sitemap":
			return "\\Concrete\\Core\\Application\\Service\\Dashboard\\Sitemap";
		case "helper/concrete/file":
			return "\\Concrete\\Core\\File\\Service\\Application";
		case "helper/concrete/file_manager":
			return "\\Concrete\\Core\\Application\\Service\\FileManager";
		case "helper/concrete/ui":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface";
		case "helper/concrete/ui/help":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help";
		case "helper/concrete/ui/menu":
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Menu";
		case "helper/concrete/urls":
			return "\\Concrete\\Core\\Application\\Service\\Urls";
		case "helper/concrete/user":
			return "\\Concrete\\Core\\Application\\Service\\User";
		case "helper/concrete/validation":
			return "\\Concrete\\Core\\Application\\Service\\Validation";
		case "helper/encryption":
			return "\\Concrete\\Core\\Encryption\\EncryptionService";
		case "helper/feed":
			return "\\Concrete\\Core\\Feed\\FeedService";
		case "helper/file":
			return "\\Concrete\\Core\\File\\Service\\File";
		case "helper/form":
			return "\\Concrete\\Core\\Form\\Service\\Form";
		case "helper/form/attribute":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Attribute";
		case "helper/form/color":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Color";
		case "helper/form/date_time":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\DateTime";
		case "helper/form/font":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Typography";
		case "helper/form/page_selector":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\PageSelector";
		case "helper/form/rating":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Rating";
		case "helper/form/typography":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Typography";
		case "helper/form/user_selector":
			return "\\Concrete\\Core\\Form\\Service\\Widget\\UserSelector";
		case "helper/html":
			return "\\Concrete\\Core\\Html\\Service\\Html";
		case "helper/image":
			return "\\Concrete\\Core\\File\\Image\\BasicThumbnailer";
		case "helper/json":
			return "\\Concrete\\Core\\Http\\Service\\Json";
		case "helper/lightbox":
			return "\\Concrete\\Core\\Html\\Service\\Lightbox";
		case "helper/mail":
			return "\\Concrete\\Core\\Mail\\Service";
		case "helper/mime":
			return "\\Concrete\\Core\\File\\Service\\Mime";
		case "helper/navigation":
			return "\\Concrete\\Core\\Html\\Service\\Navigation";
		case "helper/number":
			return "\\Concrete\\Core\\Utility\\Service\\Number";
		case "helper/pagination":
			return "\\Concrete\\Core\\Legacy\\Pagination";
		case "helper/rating":
			return "\\Concrete\\Attribute\\Rating\\Service";
		case "helper/security":
			return "\\Concrete\\Core\\Validation\\SanitizeService";
		case "helper/seo":
			return "\\Concrete\\Core\\Html\\Service\\Seo";
		case "helper/text":
			return "\\Concrete\\Core\\Utility\\Service\\Text";
		case "helper/url":
			return "\\Concrete\\Core\\Utility\\Service\\Url";
		case "helper/validation/antispam":
			return "\\Concrete\\Core\\Antispam\\Service";
		case "helper/validation/banned_words":
			return "\\Concrete\\Core\\Validation\\BannedWord\\Service";
		case "helper/validation/error":
			return "\\Concrete\\Core\\Error\\ErrorList\\ErrorList";
		case "helper/validation/file":
			return "\\Concrete\\Core\\File\\ValidationService";
		case "helper/validation/form":
			return "\\Concrete\\Core\\Form\\Service\\Validation";
		case "helper/validation/identifier":
			return "\\Concrete\\Core\\Utility\\Service\\Identifier";
		case "helper/validation/ip":
			return "\\Concrete\\Core\\Permission\\IPService";
		case "helper/validation/numbers":
			return "\\Concrete\\Core\\Utility\\Service\\Validation\\Numbers";
		case "helper/validation/strings":
			return "\\Concrete\\Core\\Utility\\Service\\Validation\\Strings";
		case "helper/validation/token":
			return "\\Concrete\\Core\\Validation\\CSRF\\Token";
		case "helper/xml":
			return "\\Concrete\\Core\\Utility\\Service\\Xml";
		case "helper/zip":
			return "\\Concrete\\Core\\File\\Service\\Zip";
		case "html/image":
			return "\\Concrete\\Core\\Html\\Image";
		case "http/client/curl":
			return "\\Concrete\\Core\\Http\\Client\\Client";
		case "http/client/socket":
			return "\\Concrete\\Core\\Http\\Client\\Client";
		case "Illuminate\\Config\\Repository":
			return "\\Concrete\\Core\\Config\\Repository\\Repository";
		case "image/gd":
			return "\\Imagine\\Gd\\Imagine";
		case "image/thumbnailer":
			return "\\Concrete\\Core\\File\\Image\\BasicThumbnailer";
		case "import/item/manager":
			return "\\Concrete\\Core\\Backup\\ContentImporter\\Importer\\Manager";
		case "import/value_inspector":
			return "\\Concrete\\Core\\Backup\\ContentImporter\\ValueInspector\\ValueInspector";
		case "import/value_inspector/core":
			return "\\Concrete\\Core\\Backup\\ContentImporter\\ValueInspector\\ValueInspector";
		case "ip":
			return "\\Concrete\\Core\\Permission\\IPService";
		case "location_search/config":
			return "\\Concrete\\Core\\Config\\Repository\\Liaison";
		case "log":
			return "\\Concrete\\Core\\Logging\\Logger";
		case "log/exceptions":
			return "\\Concrete\\Core\\Logging\\Logger";
		case "mail":
			return "\\Concrete\\Core\\Mail\\Service";
		case "manager/area_layout_preset_provider":
			return "\\Concrete\\Core\\Area\\Layout\\Preset\\Provider\\Manager";
		case "manager/attribute/category":
			return "\\Concrete\\Core\\Attribute\\Category\\Manager";
		case "manager/grid_framework":
			return "\\Concrete\\Core\\Page\\Theme\\GridFramework\\Manager";
		case "manager/notification/subscriptions":
			return "\\Concrete\\Core\\Notification\\Subscription\\Manager";
		case "manager/notification/types":
			return "\\Concrete\\Core\\Notification\\Type\\Manager";
		case "manager/page_type/saver":
			return "\\Concrete\\Core\\Page\\Type\\Saver\\Manager";
		case "manager/page_type/validator":
			return "\\Concrete\\Core\\Page\\Type\\Validator\\Manager";
		case "manager/search_field/express":
			return "\\Concrete\\Core\\Express\\Search\\Field\\Manager";
		case "manager/search_field/file":
			return "\\Concrete\\Core\\File\\Search\\Field\\Manager";
		case "manager/search_field/file_folder":
			return "\\Concrete\\Core\\File\\Search\\Field\\FileFolderManager";
		case "manager/search_field/page":
			return "\\Concrete\\Core\\Page\\Search\\Field\\Manager";
		case "manager/search_field/user":
			return "\\Concrete\\Core\\User\\Search\\Field\\Manager";
		case "manager/view/pagination":
			return "\\Concrete\\Core\\Search\\Pagination\\View\\Manager";
		case "manager/view/pagination/pager":
			return "\\Concrete\\Core\\Search\\Pagination\\View\\PagerManager";
		case "multilingual/detector":
			return "\\Concrete\\Core\\Multilingual\\Service\\Detector";
		case "multilingual/extractor":
			return "\\Concrete\\Core\\Multilingual\\Service\\Extractor";
		case "multilingual/interface/flag":
			return "\\Concrete\\Core\\Multilingual\\Service\\UserInterface\\Flag";
		case "oauth/factory/extractor":
			return "\\OAuth\\UserData\\ExtractorFactory";
		case "oauth/factory/service":
			return "\\OAuth\\ServiceFactory";
		case "orm/cache":
			return "\\Concrete\\Core\\Cache\\Adapter\\DoctrineCacheDriver";
		case "orm/cachedAnnotationReader":
			return "\\Doctrine\\Common\\Annotations\\CachedReader";
		case "orm/cachedSimpleAnnotationReader":
			return "\\Doctrine\\Common\\Annotations\\CachedReader";
		case "Psr\\Log\\LoggerInterface":
			return "\\Concrete\\Core\\Logging\\Logger";
		case "session":
			return "\\Symfony\\Component\\HttpFoundation\\Session\\Session";
		case "site":
			return "\\Concrete\\Core\\Site\\Service";
		case "site/type":
			return "\\Concrete\\Core\\Site\\Type\\Service";
		case "statistics/tracker":
			return "\\Concrete\\Core\\Statistics\\UsageTracker\\AggregateTracker";
		case "Symfony\\Component\\EventDispatcher\\EventDispatcherInterface":
			return "\\Symfony\\Component\\EventDispatcher\\EventDispatcher";
		case "token":
			return "\\Concrete\\Core\\Validation\\CSRF\\Token";
		case "url/canonical":
			return "\\Concrete\\Core\\Url\\UrlImmutable";
		case "url/canonical/resolver":
			return "\\Concrete\\Core\\Url\\Resolver\\CanonicalUrlResolver";
		case "url/manager":
			return "\\Concrete\\Core\\Url\\Resolver\\Manager\\ResolverManager";
		case "url/resolver/page":
			return "\\Concrete\\Core\\Url\\Resolver\\PageUrlResolver";
		case "url/resolver/path":
			return "\\Concrete\\Core\\Url\\Resolver\\PathUrlResolver";
		case "url/resolver/route":
			return "\\Concrete\\Core\\Url\\Resolver\\RouterUrlResolver";
		case "user/avatar":
			return "\\Concrete\\Core\\User\\Avatar\\AvatarService";
		case "user/registration":
			return "\\Concrete\\Core\\User\\RegistrationService";
		case "user/status":
			return "\\Concrete\\Core\\User\\StatusService";
		case "validator/password":
			return "\\Concrete\\Core\\Validator\\ValidatorManager";
		case "Zend\\Mail\\Transport\\TransportInterface":
			return "\\Zend\\Mail\\Transport\\Smtp";
		case "test":
			return "\\Mlocati\\Test\\Prova";
		}
		return null;
	}
}

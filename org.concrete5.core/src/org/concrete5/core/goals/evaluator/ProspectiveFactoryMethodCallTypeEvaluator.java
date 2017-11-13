package org.concrete5.core.goals.evaluator;

import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.ti.GoalState;
import org.eclipse.dltk.ti.IContext;
import org.eclipse.dltk.ti.ISourceModuleContext;
import org.eclipse.dltk.ti.goals.ExpressionTypeGoal;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.dltk.ti.types.ClassType;
import org.eclipse.dltk.ti.types.IEvaluatedType;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.internal.core.typeinference.evaluators.MethodCallTypeEvaluator;

@SuppressWarnings("restriction")
public class ProspectiveFactoryMethodCallTypeEvaluator extends MethodCallTypeEvaluator {
	/**
	 * Factory method flags: not a factory method.
	 */
	public static final int FACTORYMETHOD_NONE = 0x0000;

	/**
	 * Factory method flags: factory method that may return a class instance.
	 */
	public static final int FACTORYMETHOD_CLASSES = 0x0001;

	/**
	 * Factory method flags: factory method that may resolve classes.
	 */
	public static final int FACTORYMETHOD_ALIASES = 0x0002;

	private final static int STATE_INIT = 0;
	private final static int STATE_WAITING_TYPE = 1;
	private final static int STATE_CLASSFACTORYDETECTED = 2;
	private final static int STATE_NOTFACTORYMETHOD = 99;

	private int state = STATE_INIT;
	private int flags;
	private int sourceStart;
	private String methodArgument;
	private IEvaluatedType result;

	public ProspectiveFactoryMethodCallTypeEvaluator(ExpressionTypeGoal goal, int flags, String methodArgument,
			int sourceStart) {
		super(goal);
		this.flags = flags;
		this.methodArgument = methodArgument;
		this.sourceStart = sourceStart;
	}

	@Override
	public IGoal[] init() {
		IGoal goal = this.produceNextSubgoal(null, null, null);
		if (goal != null) {
			return new IGoal[] { goal };
		}
		return IGoal.NO_GOALS;
	}

	@Override
	public IGoal[] subGoalDone(IGoal subgoal, Object result, GoalState state) {
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return super.subGoalDone(subgoal, result, state);
		}
		IGoal goal = this.produceNextSubgoal(subgoal, (IEvaluatedType) result, state);
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return super.init();
		}
		if (goal != null) {
			return new IGoal[] { goal };
		}
		return IGoal.NO_GOALS;
	}

	@Override
	public Object produceResult() {
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return super.produceResult();
		}
		return this.result;
	}

	private IGoal produceNextSubgoal(IGoal previousGoal, IEvaluatedType previousResult, GoalState goalState) {
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return null;
		}
		if (this.state == STATE_INIT) {
			ExpressionTypeGoal goal = (ExpressionTypeGoal) this.goal;
			PHPCallExpression expression = (PHPCallExpression) goal.getExpression();
			ASTNode receiver = expression == null ? null : expression.getReceiver();
			if (receiver == null) {
				this.state = STATE_NOTFACTORYMETHOD;
			} else {
				this.state = STATE_WAITING_TYPE;
				return new ExpressionTypeGoal(goal.getContext(), receiver);
			}
		} else if (this.state == STATE_WAITING_TYPE) {
			if (previousResult instanceof ClassType) {
				ClassType receiverClass = (ClassType) previousResult;
				previousResult = null;
				if (this.isApplicationInstance(receiverClass)) {
					this.state = STATE_CLASSFACTORYDETECTED;
				} else {
					this.state = STATE_NOTFACTORYMETHOD;
					IType[] types = this.getParentTypes(receiverClass);
					if (types != null) {
						for (IType type : types) {
							if (type == null) {
								continue;
							}
							String[] superClasses;
							try {
								superClasses = type.getSuperClasses();
							} catch (ModelException e) {
								continue;
							}
							if (superClasses == null) {
								continue;
							}
							for (String superClass : superClasses) {
								if (superClass != null && this.isApplicationInstance(superClass)) {
									this.state = STATE_CLASSFACTORYDETECTED;
									break;
								}
							}
							if (this.state == STATE_CLASSFACTORYDETECTED) {
								break;
							}
						}
					}
				}
				if (this.state == STATE_CLASSFACTORYDETECTED) {
					if (goalState != GoalState.PRUNED) {
						this.result = this.createResultForFactoryMethod();
					}
				}
				;
			} else {
				this.state = STATE_NOTFACTORYMETHOD;
				previousResult = null;
			}
		}
		return null;
	}

	private IType[] getParentTypes(ClassType classType) {
		IContext context = this.goal.getContext();
		if (!(context instanceof ISourceModuleContext)) {
			return null;
		}
		return org.eclipse.php.internal.core.typeinference.PHPTypeInferenceUtils.getModelElements(classType,
				(ISourceModuleContext) context, this.sourceStart);
	}

	private boolean isApplicationInstance(ClassType classType) {
		return classType != null && this.isApplicationInstance(classType.getTypeName());
	}

	private boolean isApplicationInstance(String className) {
		if (className == null || className.isEmpty()) {
			return false;
		}
		String cn = className.charAt(0) == '\\' ? className.substring(1) : className;
		return className != null && ("Concrete\\Core\\Application\\Application".equalsIgnoreCase(cn) //$NON-NLS-1$
				|| "Illuminate\\Container\\Container".equalsIgnoreCase(cn)); //$NON-NLS-1$

	}

	private IEvaluatedType createResultForFactoryMethod() {
		String className = null;
		if (className == null && (this.flags & FACTORYMETHOD_ALIASES) != 0) {
			className = this.getMethodAliasedClassName();
		}
		if (className == null && (this.flags & FACTORYMETHOD_CLASSES) != 0) {
			className = this.getMethodClassName();
		}
		return className == null ? null : this.createEvaluatedType(className);
	}

	private IEvaluatedType createEvaluatedType(String className) {
		return new org.eclipse.php.internal.core.typeinference.PHPClassType(className);
	}

	private String getMethodClassName() {
		if (Character.isUpperCase(this.methodArgument.charAt(0))) {
			return this.methodArgument;
		}
		if (this.methodArgument.length() > 1 && this.methodArgument.charAt(0) == '\\') {
			if (Character.isUpperCase(this.methodArgument.charAt(1))) {
				return this.methodArgument;
			}
			if (this.methodArgument.length() > 2 && this.methodArgument.charAt(1) == '\\') {
				if (Character.isUpperCase(this.methodArgument.charAt(2))) {
					return this.methodArgument;
				}
			}
		}
		return null;

	}

	private String getMethodAliasedClassName() {
		// @todo Check the contents of .phpstorm.meta.php
		switch (this.methodArgument) {
		case "cache": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cache\\Level\\ObjectCache"; //$NON-NLS-1$
		case "cache/expensive": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cache\\Level\\ExpensiveCache"; //$NON-NLS-1$
		case "cache/overrides": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cache\\Level\\OverridesCache"; //$NON-NLS-1$
		case "cache/page": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cache\\Page\\FilePageCache"; //$NON-NLS-1$
		case "cache/request": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cache\\Level\\RequestCache"; //$NON-NLS-1$
		case "Concrete\\Core\\Captcha\\CaptchaInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Captcha\\SecurimageController"; //$NON-NLS-1$
		case "Concrete\\Core\\Captcha\\CaptchaWithPictureInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Captcha\\SecurimageController"; //$NON-NLS-1$
		case "Concrete\\Core\\Database\\EntityManagerConfigFactoryInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Database\\EntityManagerConfigFactory"; //$NON-NLS-1$
		case "Concrete\\Core\\Database\\EntityManagerFactoryInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Database\\EntityManagerFactory"; //$NON-NLS-1$
		case "Concrete\\Core\\Express\\Entry\\Formatter\\EntryFormatterInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Express\\Entry\\Formatter\\LabelFormatter"; //$NON-NLS-1$
		case "Concrete\\Core\\Express\\Formatter\\FormatterInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Express\\Formatter\\LabelFormatter"; //$NON-NLS-1$
		case "Concrete\\Core\\File\\StorageLocation\\StorageLocationInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Entity\\File\\StorageLocation\\StorageLocation"; //$NON-NLS-1$
		case "Concrete\\Core\\Http\\DispatcherInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\DefaultDispatcher"; //$NON-NLS-1$
		case "Concrete\\Core\\Http\\Middleware\\StackInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\Middleware\\MiddlewareStack"; //$NON-NLS-1$
		case "Concrete\\Core\\Http\\ResponseFactoryInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\ResponseFactory"; //$NON-NLS-1$
		case "Concrete\\Core\\Http\\ServerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\DefaultServer"; //$NON-NLS-1$
		case "Concrete\\Core\\Localization\\Translator\\TranslatorAdapterFactoryInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Localization\\Translator\\Adapter\\Core\\TranslatorAdapterFactory"; //$NON-NLS-1$
		case "Concrete\\Core\\Routing\\RouterInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Routing\\Router"; //$NON-NLS-1$
		case "Concrete\\Core\\Search\\Index\\IndexManagerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Search\\Index\\DefaultManager"; //$NON-NLS-1$
		case "Concrete\\Core\\Service\\Manager\\ManagerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Service\\Manager\\ServiceManager"; //$NON-NLS-1$
		case "Concrete\\Core\\Session\\SessionFactoryInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Session\\SessionFactory"; //$NON-NLS-1$
		case "Concrete\\Core\\Session\\SessionValidatorInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Session\\SessionValidator"; //$NON-NLS-1$
		case "Concrete\\Core\\Site\\Resolver\\DriverInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Site\\Resolver\\StandardDriver"; //$NON-NLS-1$
		case "Concrete\\Core\\Statistics\\UsageTracker\\TrackerManagerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Statistics\\UsageTracker\\AggregateTracker"; //$NON-NLS-1$
		case "Concrete\\Core\\Url\\Resolver\\Manager\\ResolverManagerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\Resolver\\Manager\\ResolverManager"; //$NON-NLS-1$
		case "Concrete\\Core\\User\\Avatar\\AvatarServiceInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\Avatar\\AvatarService"; //$NON-NLS-1$
		case "Concrete\\Core\\User\\RegistrationServiceInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\RegistrationService"; //$NON-NLS-1$
		case "Concrete\\Core\\User\\StatusServiceInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\StatusService"; //$NON-NLS-1$
		case "Concrete\\Core\\Validator\\ValidatorManagerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Validator\\ValidatorManager"; //$NON-NLS-1$
		case "config": //$NON-NLS-1$
			return "\\Concrete\\Core\\Config\\Repository\\Repository"; //$NON-NLS-1$
		case "config/database": //$NON-NLS-1$
			return "\\Concrete\\Core\\Config\\Repository\\Repository"; //$NON-NLS-1$
		case "cookie": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cookie\\CookieJar"; //$NON-NLS-1$
		case "database": //$NON-NLS-1$
			return "\\Concrete\\Core\\Database\\DatabaseManager"; //$NON-NLS-1$
		case "database/orm": //$NON-NLS-1$
			return "\\Concrete\\Core\\Database\\DatabaseManagerORM"; //$NON-NLS-1$
		case "device/manager": //$NON-NLS-1$
			return "\\Concrete\\Core\\Device\\DeviceManager"; //$NON-NLS-1$
		case "director": //$NON-NLS-1$
			return "\\Symfony\\Component\\EventDispatcher\\EventDispatcher"; //$NON-NLS-1$
		case "Doctrine\\DBAL\\Connection": //$NON-NLS-1$
			return "\\Concrete\\Core\\Database\\Connection\\Connection"; //$NON-NLS-1$
		case "Doctrine\\ORM\\EntityManagerInterface": //$NON-NLS-1$
			return "\\Doctrine\\ORM\\EntityManager"; //$NON-NLS-1$
		case "editor": //$NON-NLS-1$
			return "\\Concrete\\Core\\Editor\\CkeditorEditor"; //$NON-NLS-1$
		case "editor/image": //$NON-NLS-1$
			return "\\Concrete\\Core\\ImageEditor\\ImageEditor"; //$NON-NLS-1$
		case "editor/image/core": //$NON-NLS-1$
			return "\\Concrete\\Core\\ImageEditor\\ImageEditor"; //$NON-NLS-1$
		case "editor/image/extension/factory": //$NON-NLS-1$
			return "\\Concrete\\Core\\ImageEditor\\ExtensionFactory"; //$NON-NLS-1$
		case "element": //$NON-NLS-1$
			return "\\Concrete\\Core\\Filesystem\\ElementManager"; //$NON-NLS-1$
		case "environment": //$NON-NLS-1$
			return "\\Concrete\\Core\\Foundation\\Environment"; //$NON-NLS-1$
		case "error": //$NON-NLS-1$
			return "\\Concrete\\Core\\Error\\ErrorList\\ErrorList"; //$NON-NLS-1$
		case "express": //$NON-NLS-1$
			return "\\Concrete\\Core\\Express\\ObjectManager"; //$NON-NLS-1$
		case "express/builder/association": //$NON-NLS-1$
			return "\\Concrete\\Core\\Express\\ObjectAssociationBuilder"; //$NON-NLS-1$
		case "express/control/type/manager": //$NON-NLS-1$
			return "\\Concrete\\Core\\Express\\Form\\Control\\Type\\Manager"; //$NON-NLS-1$
		case "form/express/entry_selector": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\ExpressEntrySelector"; //$NON-NLS-1$
		case "help": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help"; //$NON-NLS-1$
		case "help/block_type": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\BlockTypeManager"; //$NON-NLS-1$
		case "help/core": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\CoreManager"; //$NON-NLS-1$
		case "help/dashboard": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\DashboardManager"; //$NON-NLS-1$
		case "help/panel": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help\\PanelManager"; //$NON-NLS-1$
		case "helper/ajax": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\Service\\Ajax"; //$NON-NLS-1$
		case "helper/arrays": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Arrays"; //$NON-NLS-1$
		case "helper/concrete/asset_library": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\FileManager"; //$NON-NLS-1$
		case "helper/concrete/avatar": //$NON-NLS-1$
			return "\\Concrete\\Core\\Legacy\\Avatar"; //$NON-NLS-1$
		case "helper/concrete/composer": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\Composer"; //$NON-NLS-1$
		case "helper/concrete/dashboard": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\Dashboard"; //$NON-NLS-1$
		case "helper/concrete/dashboard/sitemap": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\Dashboard\\Sitemap"; //$NON-NLS-1$
		case "helper/concrete/file": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Service\\Application"; //$NON-NLS-1$
		case "helper/concrete/file_manager": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\FileManager"; //$NON-NLS-1$
		case "helper/concrete/ui": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface"; //$NON-NLS-1$
		case "helper/concrete/ui/help": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Help"; //$NON-NLS-1$
		case "helper/concrete/ui/menu": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\UserInterface\\Menu"; //$NON-NLS-1$
		case "helper/concrete/urls": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\Urls"; //$NON-NLS-1$
		case "helper/concrete/user": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\User"; //$NON-NLS-1$
		case "helper/concrete/validation": //$NON-NLS-1$
			return "\\Concrete\\Core\\Application\\Service\\Validation"; //$NON-NLS-1$
		case "helper/encryption": //$NON-NLS-1$
			return "\\Concrete\\Core\\Encryption\\EncryptionService"; //$NON-NLS-1$
		case "helper/feed": //$NON-NLS-1$
			return "\\Concrete\\Core\\Feed\\FeedService"; //$NON-NLS-1$
		case "helper/file": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Service\\File"; //$NON-NLS-1$
		case "helper/form": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Form"; //$NON-NLS-1$
		case "helper/form/attribute": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Attribute"; //$NON-NLS-1$
		case "helper/form/color": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Color"; //$NON-NLS-1$
		case "helper/form/date_time": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\DateTime"; //$NON-NLS-1$
		case "helper/form/font": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Typography"; //$NON-NLS-1$
		case "helper/form/page_selector": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\PageSelector"; //$NON-NLS-1$
		case "helper/form/rating": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Rating"; //$NON-NLS-1$
		case "helper/form/typography": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\Typography"; //$NON-NLS-1$
		case "helper/form/user_selector": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Widget\\UserSelector"; //$NON-NLS-1$
		case "helper/html": //$NON-NLS-1$
			return "\\Concrete\\Core\\Html\\Service\\Html"; //$NON-NLS-1$
		case "helper/image": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Image\\BasicThumbnailer"; //$NON-NLS-1$
		case "helper/json": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\Service\\Json"; //$NON-NLS-1$
		case "helper/lightbox": //$NON-NLS-1$
			return "\\Concrete\\Core\\Html\\Service\\Lightbox"; //$NON-NLS-1$
		case "helper/mail": //$NON-NLS-1$
			return "\\Concrete\\Core\\Mail\\Service"; //$NON-NLS-1$
		case "helper/mime": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Service\\Mime"; //$NON-NLS-1$
		case "helper/navigation": //$NON-NLS-1$
			return "\\Concrete\\Core\\Html\\Service\\Navigation"; //$NON-NLS-1$
		case "helper/number": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Number"; //$NON-NLS-1$
		case "helper/pagination": //$NON-NLS-1$
			return "\\Concrete\\Core\\Legacy\\Pagination"; //$NON-NLS-1$
		case "helper/rating": //$NON-NLS-1$
			return "\\Concrete\\Attribute\\Rating\\Service"; //$NON-NLS-1$
		case "helper/security": //$NON-NLS-1$
			return "\\Concrete\\Core\\Validation\\SanitizeService"; //$NON-NLS-1$
		case "helper/seo": //$NON-NLS-1$
			return "\\Concrete\\Core\\Html\\Service\\Seo"; //$NON-NLS-1$
		case "helper/text": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Text"; //$NON-NLS-1$
		case "helper/url": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Url"; //$NON-NLS-1$
		case "helper/validation/antispam": //$NON-NLS-1$
			return "\\Concrete\\Core\\Antispam\\Service"; //$NON-NLS-1$
		case "helper/validation/banned_words": //$NON-NLS-1$
			return "\\Concrete\\Core\\Validation\\BannedWord\\Service"; //$NON-NLS-1$
		case "helper/validation/error": //$NON-NLS-1$
			return "\\Concrete\\Core\\Error\\ErrorList\\ErrorList"; //$NON-NLS-1$
		case "helper/validation/file": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\ValidationService"; //$NON-NLS-1$
		case "helper/validation/form": //$NON-NLS-1$
			return "\\Concrete\\Core\\Form\\Service\\Validation"; //$NON-NLS-1$
		case "helper/validation/identifier": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Identifier"; //$NON-NLS-1$
		case "helper/validation/ip": //$NON-NLS-1$
			return "\\Concrete\\Core\\Permission\\IPService"; //$NON-NLS-1$
		case "helper/validation/numbers": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Validation\\Numbers"; //$NON-NLS-1$
		case "helper/validation/strings": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Validation\\Strings"; //$NON-NLS-1$
		case "helper/validation/token": //$NON-NLS-1$
			return "\\Concrete\\Core\\Validation\\CSRF\\Token"; //$NON-NLS-1$
		case "helper/xml": //$NON-NLS-1$
			return "\\Concrete\\Core\\Utility\\Service\\Xml"; //$NON-NLS-1$
		case "helper/zip": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Service\\Zip"; //$NON-NLS-1$
		case "html/image": //$NON-NLS-1$
			return "\\Concrete\\Core\\Html\\Image"; //$NON-NLS-1$
		case "http/client/curl": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\Client\\Client"; //$NON-NLS-1$
		case "http/client/socket": //$NON-NLS-1$
			return "\\Concrete\\Core\\Http\\Client\\Client"; //$NON-NLS-1$
		case "Illuminate\\Config\\Repository": //$NON-NLS-1$
			return "\\Concrete\\Core\\Config\\Repository\\Repository"; //$NON-NLS-1$
		case "image/gd": //$NON-NLS-1$
			return "\\Imagine\\Gd\\Imagine"; //$NON-NLS-1$
		case "image/thumbnailer": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Image\\BasicThumbnailer"; //$NON-NLS-1$
		case "import/item/manager": //$NON-NLS-1$
			return "\\Concrete\\Core\\Backup\\ContentImporter\\Importer\\Manager"; //$NON-NLS-1$
		case "import/value_inspector": //$NON-NLS-1$
			return "\\Concrete\\Core\\Backup\\ContentImporter\\ValueInspector\\ValueInspector"; //$NON-NLS-1$
		case "import/value_inspector/core": //$NON-NLS-1$
			return "\\Concrete\\Core\\Backup\\ContentImporter\\ValueInspector\\ValueInspector"; //$NON-NLS-1$
		case "ip": //$NON-NLS-1$
			return "\\Concrete\\Core\\Permission\\IPService"; //$NON-NLS-1$
		case "location_search/config": //$NON-NLS-1$
			return "\\Concrete\\Core\\Config\\Repository\\Liaison"; //$NON-NLS-1$
		case "log": //$NON-NLS-1$
			return "\\Concrete\\Core\\Logging\\Logger"; //$NON-NLS-1$
		case "log/exceptions": //$NON-NLS-1$
			return "\\Concrete\\Core\\Logging\\Logger"; //$NON-NLS-1$
		case "mail": //$NON-NLS-1$
			return "\\Concrete\\Core\\Mail\\Service"; //$NON-NLS-1$
		case "manager/area_layout_preset_provider": //$NON-NLS-1$
			return "\\Concrete\\Core\\Area\\Layout\\Preset\\Provider\\Manager"; //$NON-NLS-1$
		case "manager/attribute/category": //$NON-NLS-1$
			return "\\Concrete\\Core\\Attribute\\Category\\Manager"; //$NON-NLS-1$
		case "manager/grid_framework": //$NON-NLS-1$
			return "\\Concrete\\Core\\Page\\Theme\\GridFramework\\Manager"; //$NON-NLS-1$
		case "manager/notification/subscriptions": //$NON-NLS-1$
			return "\\Concrete\\Core\\Notification\\Subscription\\Manager"; //$NON-NLS-1$
		case "manager/notification/types": //$NON-NLS-1$
			return "\\Concrete\\Core\\Notification\\Type\\Manager"; //$NON-NLS-1$
		case "manager/page_type/saver": //$NON-NLS-1$
			return "\\Concrete\\Core\\Page\\Type\\Saver\\Manager"; //$NON-NLS-1$
		case "manager/page_type/validator": //$NON-NLS-1$
			return "\\Concrete\\Core\\Page\\Type\\Validator\\Manager"; //$NON-NLS-1$
		case "manager/search_field/express": //$NON-NLS-1$
			return "\\Concrete\\Core\\Express\\Search\\Field\\Manager"; //$NON-NLS-1$
		case "manager/search_field/file": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Search\\Field\\Manager"; //$NON-NLS-1$
		case "manager/search_field/file_folder": //$NON-NLS-1$
			return "\\Concrete\\Core\\File\\Search\\Field\\FileFolderManager"; //$NON-NLS-1$
		case "manager/search_field/page": //$NON-NLS-1$
			return "\\Concrete\\Core\\Page\\Search\\Field\\Manager"; //$NON-NLS-1$
		case "manager/search_field/user": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\Search\\Field\\Manager"; //$NON-NLS-1$
		case "manager/view/pagination": //$NON-NLS-1$
			return "\\Concrete\\Core\\Search\\Pagination\\View\\Manager"; //$NON-NLS-1$
		case "manager/view/pagination/pager": //$NON-NLS-1$
			return "\\Concrete\\Core\\Search\\Pagination\\View\\PagerManager"; //$NON-NLS-1$
		case "multilingual/detector": //$NON-NLS-1$
			return "\\Concrete\\Core\\Multilingual\\Service\\Detector"; //$NON-NLS-1$
		case "multilingual/extractor": //$NON-NLS-1$
			return "\\Concrete\\Core\\Multilingual\\Service\\Extractor"; //$NON-NLS-1$
		case "multilingual/interface/flag": //$NON-NLS-1$
			return "\\Concrete\\Core\\Multilingual\\Service\\UserInterface\\Flag"; //$NON-NLS-1$
		case "oauth/factory/extractor": //$NON-NLS-1$
			return "\\OAuth\\UserData\\ExtractorFactory"; //$NON-NLS-1$
		case "oauth/factory/service": //$NON-NLS-1$
			return "\\OAuth\\ServiceFactory"; //$NON-NLS-1$
		case "orm/cache": //$NON-NLS-1$
			return "\\Concrete\\Core\\Cache\\Adapter\\DoctrineCacheDriver"; //$NON-NLS-1$
		case "orm/cachedAnnotationReader": //$NON-NLS-1$
			return "\\Doctrine\\Common\\Annotations\\CachedReader"; //$NON-NLS-1$
		case "orm/cachedSimpleAnnotationReader": //$NON-NLS-1$
			return "\\Doctrine\\Common\\Annotations\\CachedReader"; //$NON-NLS-1$
		case "Psr\\Log\\LoggerInterface": //$NON-NLS-1$
			return "\\Concrete\\Core\\Logging\\Logger"; //$NON-NLS-1$
		case "session": //$NON-NLS-1$
			return "\\Symfony\\Component\\HttpFoundation\\Session\\Session"; //$NON-NLS-1$
		case "site": //$NON-NLS-1$
			return "\\Concrete\\Core\\Site\\Service"; //$NON-NLS-1$
		case "site/type": //$NON-NLS-1$
			return "\\Concrete\\Core\\Site\\Type\\Service"; //$NON-NLS-1$
		case "statistics/tracker": //$NON-NLS-1$
			return "\\Concrete\\Core\\Statistics\\UsageTracker\\AggregateTracker"; //$NON-NLS-1$
		case "Symfony\\Component\\EventDispatcher\\EventDispatcherInterface": //$NON-NLS-1$
			return "\\Symfony\\Component\\EventDispatcher\\EventDispatcher"; //$NON-NLS-1$
		case "token": //$NON-NLS-1$
			return "\\Concrete\\Core\\Validation\\CSRF\\Token"; //$NON-NLS-1$
		case "url/canonical": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\UrlImmutable"; //$NON-NLS-1$
		case "url/canonical/resolver": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\Resolver\\CanonicalUrlResolver"; //$NON-NLS-1$
		case "url/manager": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\Resolver\\Manager\\ResolverManager"; //$NON-NLS-1$
		case "url/resolver/page": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\Resolver\\PageUrlResolver"; //$NON-NLS-1$
		case "url/resolver/path": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\Resolver\\PathUrlResolver"; //$NON-NLS-1$
		case "url/resolver/route": //$NON-NLS-1$
			return "\\Concrete\\Core\\Url\\Resolver\\RouterUrlResolver"; //$NON-NLS-1$
		case "user/avatar": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\Avatar\\AvatarService"; //$NON-NLS-1$
		case "user/registration": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\RegistrationService"; //$NON-NLS-1$
		case "user/status": //$NON-NLS-1$
			return "\\Concrete\\Core\\User\\StatusService"; //$NON-NLS-1$
		case "validator/password": //$NON-NLS-1$
			return "\\Concrete\\Core\\Validator\\ValidatorManager"; //$NON-NLS-1$
		case "Zend\\Mail\\Transport\\TransportInterface": //$NON-NLS-1$
			return "\\Zend\\Mail\\Transport\\Smtp"; //$NON-NLS-1$
		case "test": //$NON-NLS-1$
			return "\\Mlocati\\Test\\Prova"; //$NON-NLS-1$
		}
		return null;
	}
}

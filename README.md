# Spring Cloud Gateway Server Web MVC — YAML에서 필터 빈을 이름으로 참조하는 템플릿

MVC 스택을 쓰면서 WebFlux 스택과 같은 YAML 작성 경험을 주는 얇은 커스터마이징 레이어. Kotlin.

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: order
              uri: http://order-service
              predicates:
                - Path=/orders/**
              filters:
                - name: authFilter      # ← @GatewayFilterBean 빈 이름
                - name: tenantFilter
                - AddRequestHeader=X-Gateway,mvc-extensions   # built-in 축약 표기와 혼용 가능
```

- 리스트 순서 = 실행 순서
- 필터는 `@GatewayFilterBean` 하나로 등록, 생성자 주입 자유
- 별칭 메서드의 존재와 이름은 **컴파일러가 강제**한다
- 나머지 실수(빈 타입 불일치·이름 충돌·`@JvmStatic` 누락·YAML 오타)는 **포트가 열리기 전에** 기동 실패

## 프로젝트 구조

같은 레벨의 두 프로젝트. 루트에는 코드가 없고 둘을 묶기만 한다.

```
.
├── settings.gradle.kts      루트는 집계 전용 (build.gradle.kts 없음)
├── gateway/                 게이트웨이          :8080
└── downstream/              라우팅 대상 더미     :8081
```

게이트웨이 안은 프레임워크와 애플리케이션으로 나뉘어 있다. 의존은 **애플리케이션 → 프레임워크**
한 방향이고(`framework/`에서 `routing/`·`security/`를 import 하는 곳은 없다), 애플리케이션이 쓰는
프레임워크 심볼은 7개뿐이다 — `@GatewayFilterBean`, `GatewayFilterAlias`, `beanFilter`,
`factoryFilter`, `FilterTrace`, `GatewayErrorCode`, `GatewayException`.

```
com.example.gateway
├── framework/filter/              재사용 레이어 — 애플리케이션을 전혀 모른다
│   ├── GatewayFilterBean.kt           @GatewayFilterBean
│   ├── GatewayFilterAlias.kt          alias() 계약
│   ├── BeanFilters.kt                 beanFilter<T>(), beanFilter(name), factoryFilter<F>()
│   ├── delegate/                      BeanTarget, BeanDelegatingFilter        (internal)
│   ├── registry/                      GatewayFilterBeanRegistry, AnnotatedFilterBeanDiscoverer,
│   │                                  BeanAliasFilterSupplier
│   ├── trace/                         FilterTrace
│   ├── validation/                    GatewayFilterValidator
│   └── config/                        GatewayFilterProperties, GatewayFilterConfiguration
│
├── framework/error/               에러 응답 계약
│   ├── GatewayErrorCode.kt            status / code / message 인터페이스
│   ├── GatewayException.kt            필터가 던지는 예외
│   ├── ErrorResponse.kt               error_code / error_message 바디
│   ├── FrameworkErrorCode.kt          INTERNAL_ERROR
│   ├── GatewayExceptionHandler.kt     @ControllerAdvice (selector 없음)
│   └── GatewayErrorAttributes.kt      /error 로 떨어지는 것까지 같은 형식으로
│
├── routing/error/                 애플리케이션 에러코드
│   └── RouteErrorCode.kt              NOT_FOUND_TOKEN, ...
│
├── routing/filter/                애플리케이션 라우팅 필터
│   ├── AuthFilter.kt  TenantFilter.kt  JsonEnvelopeFilter.kt
│   ├── UserRequirementFilterFactory.kt
│   ├── GatewayArgsFilterAliases.kt        인자 받는 필터의 수동 별칭
│   └── GatewayArgsFilterConfiguration.kt  그 별칭을 @Bean 으로 등록
│
└── security/
    └── TokenIntrospector.kt       필터가 아닌 협력 빈
```

프레임워크가 애플리케이션 별칭 클래스를 **이름으로 알지 못한다**는 점이 핵심이다.
`BeanAliasFilterSupplier`는 생성자로 별칭 클래스를 받고, 애플리케이션이 자기 설정
(`GatewayArgsFilterConfiguration`, `routing/filter/`)에서 그 빈을 등록한다. 프레임워크는 `ObjectProvider`로 **있으면 쓰고
없으면 넘어간다** — 수동 별칭을 안 쓰는 프로젝트는 그 설정을 두지 않으면 된다.

경계는 패키지로만 나뉘어 있어 컴파일러가 강제하지는 않는다. 막고 싶으면 `framework/`를 별도
Gradle 모듈로 떼면 되고, 지금 구조가 그 작업을 파일 이동만으로 만들어 둔다.

서로 의존하지 않는다 — `project(":downstream")` 참조는 어디에도 없다. 각자 따로 띄우고
따로 빌드한다.

```bash
./gradlew :gateway:bootRun      # :8080
./gradlew :downstream:bootRun   # :8081
./gradlew :gateway:build        # 게이트웨이만 빌드 + 테스트
./gradlew build                 # 둘 다
```

플러그인 버전은 `settings.gradle.kts`의 `pluginManagement`에 모아 두어 두 빌드 스크립트가
버전 없이 같은 플러그인을 적용한다.

## 왜 이런 레이어가 필요한가

Gateway Server **Web MVC**는 YAML `filters`의 `name`을 빈 이름이 아니라
`FilterSupplier`가 공개한 **static 메서드 이름**에 매핑한다
(`RouterFunctionHolderFactory.translate()` → `StringUtils.uncapitalize(name)` → operation 맵 조회).
static 메서드에는 생성자 주입을 할 수 없으므로, 필터당 static 별칭을 두고 요청 시점에
`MvcUtils.getApplicationContext(request)`로 실제 빈에 위임한다.

```
YAML  - name: authFilter
  │
  │  AnnotatedFilterBeanDiscoverer 가 @GatewayFilterBean 빈을
  │  빈 이름을 키로 operations 맵에 등록해 둔다
  ▼
AuthFilter.alias()                         companion object : GatewayFilterAlias (@JvmStatic)
  │                                        ← 컴파일러가 존재와 이름을 강제
  ▼
beanFilter<AuthFilter>() → BeanDelegatingFilter
  │  첫 요청에 getBean(AuthFilter::class.java) 후 캐시
  ▼
AuthFilter  (@GatewayFilterBean, 생성자 주입 자유)
```

**코드 생성을 쓰지 않은 이유.** 빈 이름당 static 메서드를 런타임(ByteBuddy)이나 컴파일타임(KSP)에
생성하면 별칭을 아예 안 써도 되지만, 전자는 바이트코드 생성과 AOT/native 비친화를 들여오고 후자는
별도 프로세서 모듈이 필요한 데다 **KSP가 아직 Kotlin 2.4를 지원하지 않아 Kotlin 다운그레이드가
강제된다**(최신 2.3.12 = Kotlin 2.3.x). 그래서 메서드는 사람이 쓰고, 빠뜨릴 수 없게만 만들었다.

**메서드명을 컴파일러가 강제하지 못하는 이유와 우회.** Kotlin/Java는 static 멤버를 abstract로
선언할 수 없어서, 어떤 상위 타입도 "이름이 X인 static 메서드가 있어야 한다"를 강제할 수 없다.
그래서 이름은 인터페이스로 **`alias`로 고정**하고, YAML 이름은 **빈 이름에서** 가져온다. 그러려면
operations 맵의 키가 메서드명이 아니어야 하므로 [`AnnotatedFilterBeanDiscoverer`]
(gateway/src/main/kotlin/com/example/gateway/framework/filter/registry/AnnotatedFilterBeanDiscoverer.kt)가
게이트웨이의 `FilterBeanFactoryDiscoverer`를 대신한다.

## 필터 추가하기 (2단계)

1. `HandlerFilterFunction<ServerResponse, ServerResponse>` 빈을 만들고 `@GatewayFilterBean`을 붙인다.
   companion object가 `GatewayFilterAlias`를 구현하게 하면 **컴파일러가 `alias()`를 강제**한다.

   ```kotlin
   @GatewayFilterBean
   class RateLimitFilter(
       private val buckets: BucketRegistry,   // 생성자 주입 자유롭게 사용
   ) : HandlerFilterFunction<ServerResponse, ServerResponse> {

       override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse {
           // ...
       }

       companion object : GatewayFilterAlias {
           @JvmStatic
           override fun alias() = beanFilter<RateLimitFilter>()
       }
   }
   ```

2. 라우트 YAML에 `- name: rateLimitFilter`를 넣는다. 빈 이름은
   `@GatewayFilterBean`이 클래스명에서 뽑는다(`RateLimitFilter` → `rateLimitFilter`).
   다른 이름을 쓰려면 `@GatewayFilterBean("ratelimit")`.

중앙 레지스트리에 한 줄 추가하는 단계는 없다. 빠뜨릴 수 있는 곳이 줄었고, 남은 실수는 전부
컴파일 에러이거나 기동 실패다.

요청만 손대는 필터라면 `Function<ServerRequest, ServerRequest>`로 작성하고 빈에서
`HandlerFilterFunction.ofRequestProcessor(fn)`으로 감싸 노출하면 된다.

### 강제사항 정리

| | |
| --- | --- |
| `companion object : GatewayFilterAlias`를 쓰고 `alias()` 누락/오타 | ✅ **컴파일 에러** |
| `alias()`의 반환 타입이 틀림 | ✅ **컴파일 에러** |
| `beanFilter<T>()`의 `T`가 필터가 아님 | ✅ **컴파일 에러** (reified 타입 제약) |
| `@JvmStatic` 누락 | ❌ → 기동 실패 |
| `@GatewayFilterBean`만 붙이고 companion 미작성 | ❌ → 기동 실패 |
| `@GatewayFilterBean`인데 `HandlerFilterFunction`이 아님 | ❌ → 기동 실패 |
| `alias()`가 다른 필터를 가리킴 | ❌ → 기동 실패 |
| 빈 이름이 built-in 필터명과 충돌 | ❌ → 기동 실패 |
| YAML이 없는 필터 이름을 참조 | ❌ → 기동 실패 |

`@JvmStatic`은 인터페이스가 요구할 수 없는 유일한 부분이다. 빼먹으면 override가 `Companion`의
인스턴스 메서드로 컴파일되어 필터 클래스에 static 메서드가 아예 생기지 않으므로, 레지스트리가
그 상황을 감지해 **어디를 고쳐야 하는지 적힌 메시지로** 기동을 실패시킨다.

```
@GatewayFilterBean beans are not usable as route filters:
  - 'authFilter' (AuthFilter): no static alias() method. Add `companion object : GatewayFilterAlias
    { @JvmStatic override fun alias() = beanFilter<AuthFilter>() }` — and if the companion object is
    already there, @JvmStatic is what is missing (...)
```

### 필터에서 에러 반환하기

필터는 단락용 `ServerResponse`를 직접 만들어 반환해도 되고, **예외를 던져도 된다.** 라우트 필터는
`HandlerFunctionAdapter` → `DispatcherServlet.doDispatch` 안에서 실행되므로 예외가 평소의
`HandlerExceptionResolver` 체인을 그대로 탄다. 던지는 쪽을 쓰면 **에러 바디를 한 곳에서** 정할 수
있다.

```kotlin
// 1) 에러코드 선언 (애플리케이션)
enum class RouteErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : GatewayErrorCode {
    NOT_FOUND_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 없거나 인증정보가 올바르지 않습니다."),
    ;
    override val code get() = name
}

// 2) 필터에서 던진다
val userId = introspector.resolveUserId(header)
    ?: throw GatewayException(RouteErrorCode.NOT_FOUND_TOKEN)
```
```
HTTP/1.1 401
{"error_code":"NOT_FOUND_TOKEN","error_message":"토큰이 없거나 인증정보가 올바르지 않습니다."}
```

던져도 다운스트림은 호출되지 않는다 — 단락은 그대로다.

| 상황 | 처리 주체 | 응답 |
| --- | --- | --- |
| `GatewayException` | `GatewayExceptionHandler` (`@ControllerAdvice`) | 에러코드의 status + code/message |
| `ResponseStatusException` | `ResponseStatusExceptionResolver` → `/error` | 그 status + status 이름 |
| 그 외 예외 | 전 resolver 통과 후 `/error` | 500 + `INTERNAL_ERROR` (원본은 로그에만) |
| 매칭되는 라우트 없음 | `sendError` → `/error` | 404 + `NOT_FOUND` |

마지막 두 줄을 `GatewayErrorAttributes`가 담당한다. **`@ExceptionHandler(Exception::class)` catch-all을
쓰지 않은 이유**가 여기 있다: `ExceptionHandlerExceptionResolver`는 advice 중 **매칭되는 메서드를 가진
첫 번째**를 쓰는데, `@Order`가 없는 advice도 `LOWEST_PRECEDENCE`라서 catch-all을 아무리 뒤로 밀어도
애플리케이션 advice를 빈 순서에 따라 가려 버린다(실제로 테스트가 이걸 잡아냈다). `ErrorAttributes`를
갈아끼우면 모든 resolver가 포기한 뒤에만 불리므로 무엇도 가리지 않고, 예외가 아예 발생하지 않는
404까지 덮는다.

> **`@ControllerAdvice`에 selector를 걸면 안 된다.** `basePackages`·`assignableTypes`·`annotations`는
> 핸들러 타입을 기준으로 판정하는데, 라우트의 핸들러는 `HandlerMethod`가 아니라 `HandlerFunction`이라
> 비교 대상이 없어 **조용히 매칭되지 않는다.** `GatewayFilterErrorHandlingTests`가 고정해 두었다.

### 라우트별 인자를 받는 필터

`@GatewayFilterBean`은 **무인자 필터**만 표현한다. 라우트마다 설정이 달라야 하는 필터는 단일 빈이
될 수 없으므로 **팩토리 빈 + 수동 별칭**을 쓴다. 게이트웨이가 별칭을 메서드명으로 찾으므로 이쪽은
함수 이름이 곧 YAML 이름이다.

```kotlin
// 팩토리는 평범한 빈 — 생성자 주입 그대로
@Component
class UserRequirementFilterFactory(private val introspector: TokenIntrospector) {
    fun create(mode: String): HandlerFilterFunction<ServerResponse, ServerResponse> = ...
}

// 수동 별칭: 함수 이름 = YAML 이름, 파라미터 이름 = args 키
object GatewayArgsFilterAliases {
    @JvmStatic
    fun requireUser(mode: String) =
        factoryFilter<UserRequirementFilterFactory>("requireUser") { it.create(mode) }
}
```
```yaml
filters:
  - name: requireUser
    args:
      mode: strict      # or lenient
```

인자 바인딩은 **파라미터 이름 기반**이다(`RouterFunctionHolderFactory.matchOperation()`이
`parameterCount == args.size()`와 파라미터 이름 포함 여부를 본다). Kotlin은 기본적으로
`MethodParameters`를 생성하지 않으므로 `gateway/build.gradle.kts`에서 `javaParameters = true`를
켜 두었다. 축약 표기까지 열려면 `@Shortcut`
(`org.springframework.cloud.gateway.server.mvc.common.Shortcut`)을 붙인다.

`factoryFilter`의 첫 인자는 요청 트레이스에 찍히는 이름이다 — 트레이스는 라우트 YAML과 나란히
읽는 용도라서 팩토리 클래스명이 들어가면 줄이 맞지 않는다.

## 기동 시 검사

검사는 두 곳에서 돈다. 둘 다 웹서버가 뜨기 전이다.

- [`AnnotatedFilterBeanDiscoverer`](gateway/src/main/kotlin/com/example/gateway/framework/filter/registry/AnnotatedFilterBeanDiscoverer.kt)
  — 라우트를 빌드하는 중에 `@GatewayFilterBean` 빈을 등록하면서 검사한다
- [`GatewayFilterValidator`](gateway/src/main/kotlin/com/example/gateway/framework/filter/validation/GatewayFilterValidator.kt)
  — `SmartInitializingSingleton`. `finishBeanFactoryInitialization()` 단계이므로 `finishRefresh()`의
  웹서버 기동보다 앞이다

둘은 [`GatewayFilterBeanRegistry`](gateway/src/main/kotlin/com/example/gateway/framework/filter/registry/GatewayFilterBeanRegistry.kt)를
공유한다. **라우트가 0개면 디스커버러가 아예 호출되지 않기** 때문에, 검증기가 같은 검사를 한 번 더
돌려 그 경우에도 빠뜨리지 않는다(멱등).

| 검사 | 내용 |
| --- | --- |
| 별칭 존재 | `@GatewayFilterBean` 빈마다 static `alias()`가 있는지. `@JvmStatic` 누락을 여기서 잡는다 |
| 빈 타입 | 어노테이션이 붙은 것이 실제로 `HandlerFilterFunction`인지 |
| 별칭 타깃 | `alias()`가 자기 자신에게 위임하는지. `AuthFilter.alias()`가 `beanFilter<TenantFilter>()`를 반환하는 복붙 실수를 잡는다 |
| 이름 충돌 | 빈 이름·별칭 이름이 built-in 필터명(`addRequestHeader`, `retry`, `circuitBreaker`, …)과 겹치는지. 라우트의 필터 이름은 **인자 개수로** 해석되므로(`findOperation`), 인자 개수까지 같은 충돌이면 **어느 쪽이 등록됐는지에 따라** 라우트의 의미가 달라진다. 따지지 않고 거부한다 |
| 수동 별칭 | static인지, 무인자 별칭이 가리키는 빈이 존재하고 타입이 맞는지 |

실패는 **모아서 한 번에** 보고한다 — 한 번 재시작할 때마다 하나씩 고치지 않도록.

금지 이름 목록은 손으로 관리하지 않는다. 등록된 `FilterSupplier` 빈들과
`FilterDiscoverer`(spring.factories 경로)에서 실시간으로 수집해 교차 검사하므로 게이트웨이를
올려도 목록이 낡지 않는다. 5.0.3 기준으로 built-in 필터는 전부 `@Bean` 공급자이고
spring.factories 에는 `FilterSupplier` 가 하나도 없으므로, 후자는 방어용 경로다.

```yaml
# 전부 기본 true
gateway:
  filter:
    validate-beans: true
    fail-on-name-collision: true
```

**YAML이 모르는 필터를 참조하는 경우**는 이 프로젝트가 따로 처리하지 않는다.
`routerFunctionMapping`이 refresh 중 `RouterFunction` 빈들을 모으면서 라우트가 전부 빌드되기 때문에
이미 기동이 실패한다. 이 동작은 [`StartupFailureTests`](gateway/src/test/kotlin/com/example/failfast/StartupFailureTests.kt)가
고정해 두었으므로, 게이트웨이 업그레이드로 라우트 빌드가 지연되면 테스트가 깨져서 알 수 있다.

### 컴포넌트 스캐너는 따로 두지 않았다

`@GatewayFilterBean`은 `@Component`로 메타 어노테이션돼 있다. 그래서 `@SpringBootApplication`의
기본 컴포넌트 스캔이 그대로 수집하고, 빈 이름도 `AnnotationBeanNameGenerator`가
`AuthFilter` → `authFilter`로 만들어 준다 — 우리가 원하는 YAML 이름과 정확히 일치한다. 조회는
`ListableBeanFactory.getBeanNamesForAnnotation()` 한 줄이다.

스캐너를 직접 만들어야 하는 경우는 **컴포넌트 스캔 범위 밖의 패키지**를 뒤져야 하거나 **스프링 빈이
아닌 클래스**를 등록해야 할 때인데 둘 다 해당하지 않는다. 직접 만들면 `@ComponentScan` 설정·프로필·
조건부 등록과 어긋날 위험만 생긴다.

### 게이트웨이 내부에 의존하는 단 한 곳

`AnnotatedFilterBeanDiscoverer`가 효력을 가지는 이유는 게이트웨이의 `RouterFunctionHolderFactory`로
들어가는 **타입 기반 주입을 `@Primary`로 이긴다**는 것뿐이다. `FilterAutoConfiguration`이
`filterBeanFactoryDiscoverer`를 `@ConditionalOnMissingBean` 없이 선언하므로 정의 오버라이딩으로는
대체할 수 없다.

게이트웨이 업그레이드로 이 구조가 바뀌면 모든 `@GatewayFilterBean` 필터가 **조용히** 등록되지 않을
수 있다. 그래서 [`DiscovererReplacementTests`](gateway/src/test/kotlin/com/example/gateway/framework/filter/registry/DiscovererReplacementTests.kt)가
그걸 빌드 실패로 바꿔 놓았다.

### 조건부 필터

`@GatewayFilterBean`은 `@Component`이므로 `@Conditional` 계열을 그대로 붙일 수 있다. 조건이 맞지
않아 빈이 등록되지 않으면 그 필터 이름도 등록되지 않으므로, 그 이름을 쓰는 라우트가 있으면 기동이
실패한다 — 조건부 필터는 그 이름을 쓰는 라우트도 함께 조건부로 둬야 한다.

## 헤더 다루기

- 교체는 `ServerRequest.from(request).headers { it[NAME] = value }`.
  `ServerRequest.Builder.header()`는 **추가(add)** 라서 클라이언트가 보낸 값이 그대로 남는다.
  인증 컨텍스트 헤더(`X-User-Id` 등)에서는 위조 값이 섞이므로 반드시 교체를 쓴다
  ([`AuthFilter`](gateway/src/main/kotlin/com/example/gateway/routing/filter/AuthFilter.kt) 참고,
  `client supplied user id header is replaced not appended` 테스트가 이를 검증한다).
  Kotlin의 `headers[NAME] = value`는 `HttpHeaders.set`으로 컴파일된다.
- 모든 라우트에 공통으로 적용할 헤더 처리는 `HttpHeadersFilter.RequestHttpHeadersFilter` 빈으로 등록한다.
  프록시 직전에 실행되므로 라우트 필터가 설정한 값보다 **나중**이다.

## 바디 다루기

`modifyRequestBody`는 YAML 축약 문법이 없다. 필터 빈이 내부에서 들고 위임하면 YAML 라우트에서도 쓸 수 있다
([`JsonEnvelopeFilter`](gateway/src/main/kotlin/com/example/gateway/routing/filter/JsonEnvelopeFilter.kt),
[`ModifyRequestBodyDelegationTests`](gateway/src/test/kotlin/com/example/gateway/routing/filter/ModifyRequestBodyDelegationTests.kt)에서 실제로 동작 확인).

```kotlin
HandlerFilterFunction.ofRequestProcessor(
    BeforeFilterFunctions.modifyRequestBody(
        String::class.java,
        String::class.java,
        MediaType.APPLICATION_JSON_VALUE,
    ) { _, body -> /* body 는 nullable */ },
)
```

- 바디 전체가 메모리에 버퍼링된다. **업로드·스트리밍 라우트에는 쓰지 않는다.**
- 서명이나 HMAC처럼 원문 바이트 보존이 필요하면 `String::class.java` 또는 `ByteArray::class.java`로 받는다.
  `Map`이나 data class로 받으면 재직렬화 과정에서 필드 순서와 숫자 표현이 바뀐다.
- 바디가 없는 요청이면 변환 함수에 `null`이 전달된다 (Kotlin에서는 플랫폼 타입이므로 `String?`로 받는다).
- before 필터 안에서 `request.body(...)`를 직접 읽지 않는다. 스트림이 소모된다.
  읽기만 할 때도 `modifyRequestBody`로 그대로 통과시키는 방식으로 위임한다.

## 실행 순서

라우트 필터(YAML 리스트 순서) → `RequestHttpHeadersFilter` 빈들(X-Forwarded 처리 등) → 프록시.
권장 순서는 **위조 헤더 제거 → 인증/컨텍스트 주입 → 바디 변환**이다.

`BeanDelegatingFilter`가 모든 bean 위임 필터에 공통 트레이스를 남기므로, 실제 순서를 로그로 바로 볼 수 있다
(`com.example.gateway` 레벨 DEBUG, 기본 설정에 포함). 예시는 아래 "동작 확인" 참고.

## 빌드와 실행

터미널 두 개를 쓰거나 `&`로 백그라운드에 둔다. 순서는 상관없다 — 게이트웨이는 다운스트림이
떠 있지 않아도 기동한다(라우트는 기동 시 빌드되지만 연결은 첫 요청에 일어난다).

```bash
./gradlew :downstream:bootRun   # :8081  라우팅 타겟 (들어온 요청을 그대로 echo)
./gradlew :gateway:bootRun      # :8080  게이트웨이
```

`gateway/src/main/resources/application.yml`의 `uri: ${downstream.url:http://localhost:8081}`가
더미를 가리키므로 추가 설정은 필요 없다. 다른 대상을 보려면
`./gradlew :gateway:bootRun --args='--downstream.url=http://...'`로 덮어쓴다.

### 더미 다운스트림 (`downstream/`)

[`EchoController`](downstream/src/main/kotlin/com/example/downstream/EchoController.kt)가 `/**`를
받아 method, path, query, 헤더, 바디를 JSON으로 돌려준다. 게이트웨이 필터가 주입한 헤더
(`X-User-Id`, `X-Tenant-Id`, `X-Filter-Trace`, `X-Gateway`)는 `gatewayHeaders`로 따로 뽑아 주고
로그에도 따로 찍는다 — 검증하려는 대상이 그것이기 때문이다. 게이트웨이를 거치지 않고 :8081로
직접 들어온 요청은 `no gateway headers:` 경고를 남긴다.

매핑이 catch-all이라 게이트웨이에 라우트를 추가해도 더미는 손댈 필요가 없다.

**자동 테스트는 이 더미를 쓰지 않는다.** 테스트는 WireMock으로 다운스트림을 스텁한다 —
포트를 동적으로 잡아 병렬 실행이 안전하고, "호출되지 않았음"(401 단락)을 request journal로
단정할 수 있고, 별도 프로세스를 띄울 필요가 없다. 더미는 사람이 눈으로 확인할 때 쓴다.

### 동작 확인

```bash
# 1) 정상 라우팅 — 필터가 주입한 헤더가 다운스트림까지 도달하는지
curl -s -H 'Authorization: Bearer opaque-u1' localhost:8080/orders/1 | jq .gatewayHeaders
# {
#   "X-Filter-Trace": "authFilter,tenantFilter",   ← YAML 리스트 순서
#   "X-Gateway": "mvc-extensions",                        ← built-in AddRequestHeader
#   "X-Tenant-Id": "tenant-u1",
#   "X-User-Id": "u1"
# }

# 2) 401 단락 — 다운스트림 로그에 아무것도 남지 않아야 한다
curl -i localhost:8080/orders/2

# 3) 바디 변환 — jsonEnvelopeFilter 가 감싼 바디가 도달하는지
curl -s -X POST -H 'Authorization: Bearer opaque-u9' -H 'Content-Type: application/json' \
     -d '{"a":1}' localhost:8080/echo/thing | jq .body
# "{\"payload\":{\"a\":1}}"

# 4) 라우트별 인자 — 수동 별칭 requireUser(mode: lenient), 토큰 없이도 통과
curl -s localhost:8080/public/1 | jq .gatewayHeaders
# { "X-Filter-Trace": "requireUser", "X-User-Id": "anonymous" }
```

두 로그를 나란히 보면 전체 흐름이 그대로 보인다.

```
# :8080 게이트웨이
--> authFilter GET /orders/1
    authFilter: authenticated userId=u1, setting X-User-Id
    --> tenantFilter GET /orders/1
        tenantFilter: userId=u1 -> X-Tenant-Id=tenant-u1
    <-- tenantFilter [200]
<-- authFilter [200]

# :8081 더미
<< GET /orders/1
   X-Filter-Trace: authFilter,tenantFilter
   X-Gateway: mvc-extensions
   X-Tenant-Id: tenant-u1
   X-User-Id: u1
```

### fail-fast 확인

```bash
# 없는 필터 이름을 참조하는 설정 파일로 띄운다.
# (CLI 로 routes[0].filters[0].name 만 덮어쓰면 안 된다 — Spring Boot 는 리스트를
#  소스 간에 병합하지 않고 가장 우선순위 높은 소스의 것으로 통째로 교체한다)
cat > /tmp/bad.yml <<'YAML'
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: order
              uri: http://localhost:8081
              predicates: [ Path=/orders/** ]
              filters:
                - name: noSuchFilter
YAML
./gradlew :gateway:bootRun --args='--spring.config.additional-location=file:/tmp/bad.yml'
#   → IllegalArgumentException: Unable to find operation ... for noSuchFilter
```

## 버전

| | |
| --- | --- |
| Kotlin | 2.4.21 (BOM이 관리하는 2.2.21이 아님 — 아래 참고) |
| Spring Boot | 4.0.8 — Spring Cloud 2025.1.3 BOM이 핀하는 버전 |
| Spring Cloud | 2025.1.3 (Gateway 5.0.3) |
| Java | 25 |
| Gradle | 9.2.1 (Kotlin DSL) |

**Kotlin 버전을 BOM에서 가져오지 않는 이유.** Spring Cloud 2025.1.3의 BOM은 `kotlin.version=2.2.21`을
핀하는데, Kotlin 2.2.x는 **JVM target 25를 지원하지 않는다** (24로 폴백한 뒤
`Inconsistent JVM-target compatibility detected for tasks 'compileJava' (25) and 'compileKotlin' (24)`
로 빌드가 깨진다). 2.3+ 가 25를 지원하므로 플러그인 버전을 직접 지정한다. Gradle은
`kotlin-stdlib`를 요청된 버전 중 **가장 높은 것**으로 해석하므로 BOM의 2.2.21을 따로 덮어쓸 필요는 없다.
Java 25를 포기해도 된다면 toolchain을 21로 내리고 Kotlin 2.2.21을 쓰는 쪽이 BOM과 완전히 정렬된다.

`spring-boot-configuration-processor`는 빠져 있다. KSP를 지원하지 않고 kapt는 deprecated이므로,
`GatewayFilterProperties`는 IDE 자동완성 메타데이터 없이 동작한다.

### 구버전 참고 (이 저장소에서 검증하지 않음)

아래는 5.0.3 이 핀된 이 저장소에서 확인할 수 없는 내용이므로, 옮겨 쓰기 전에 해당 버전에서 직접
확인할 것.

`FilterBeanFactoryDiscoverer`와 `BeanFactoryGatewayDiscoverer`가 없던 버전에서는
`AbstractGatewayDiscoverer.loadSuppliers()`가 `SpringFactoriesLoader`만 쓰므로, 대체할 빈도 없고
`FilterSupplier`를 `@Bean`으로 등록해도 수집되지 않는다 — 즉 `@GatewayFilterBean` 방식을 쓸 수
없고 수동 별칭 경로만 남으며, 등록은 `gateway/src/main/resources/META-INF/spring.factories`로 한다.
`SpringFactoriesLoader`는 무인자 생성자를 요구하므로 서브클래스를 하나 둬야 한다.

```kotlin
class GatewayArgsFilterAliasSupplier : BeanAliasFilterSupplier(GatewayArgsFilterAliases::class.java)
```
```
org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier=\
  com.example.gateway.routing.filter.GatewayArgsFilterAliasSupplier
```

설정 prefix 가 `spring.cloud.gateway.mvc`였던 버전도 있다.

## 테스트

| 테스트 | 검증 내용 |
| --- | --- |
| `RouteFilterIntegrationTests` | `@GatewayFilterBean` 필터가 주입한 헤더가 다운스트림에 도달 / YAML 순서 = 실행 순서 / 401 단락 시 다운스트림 미호출 / 클라이언트 위조 헤더 교체 |
| `AliasNameMatchingTests` | `- name: authFilter` 와 `- name: AuthFilter` 둘 다 매칭 (uncapitalize 외 정규화 없음) |
| `BuiltInFilterRegressionTests` | 우리 디스커버러가 built-in 필터를 가리지 않음 (축약·장황 두 표기 모두) |
| `ModifyRequestBodyDelegationTests` | 필터 빈을 통한 `modifyRequestBody` 위임, 바디 없는 요청은 `null` |
| `ParameterizedAliasTests` | 수동 별칭 + 팩토리 빈으로 라우트별 `args` 가 동작 (`javaParameters` 전제 포함) |
| `DiscovererReplacementTests` | `@Primary` 가 타입 기반 주입을 이기는지, 어노테이션·수동 별칭·built-in 이 operations 맵에 공존하는지 — 게이트웨이 업그레이드 가드 |
| `GatewayFilterErrorHandlingTests` | 예외를 던지는 필터: `GatewayException` 렌더링 / `ResponseStatusException` 상태 유지 / selector 걸린 advice 미적용 / 라우트 없는 404 도 같은 바디 |
| `StartupFailureTests` | 별칭 누락 / `@JvmStatic` 누락 / 필터가 아님 / 별칭 타깃 불일치 / 이름 충돌(어노테이션·수동 양쪽) / 수동 별칭 dangling·non-static / YAML 오타 / **라우트 0개일 때도 검증됨** |

자동 테스트(`./gradlew :gateway:test`)의 다운스트림 스텁은 WireMock이고, 테스트 JVM 전체에서 하나를 공유한다
([`DownstreamStubTests`](gateway/src/test/kotlin/com/example/gateway/support/DownstreamStubTests.kt)).
`http2PlainDisabled(true)`가 필요하다: 게이트웨이는 JDK `HttpClient`로 프록시하면서 평문 h2c
업그레이드를 시도하는데, WireMock이 업그레이드를 받아들인 뒤 스트림을 리셋해서 바디가 있는 모든
요청이 500이 된다. 실제 HTTP/1.1 다운스트림은 업그레이드를 거절할 뿐이므로 스텁 쪽 특성이다.

# SCG Server MVC: YAML 라우트에서 HandlerFilterFunction 빈을 이름으로 참조하기

> 결정 사항: **`@GatewayFilterBean` + 빈 이름을 키로 쓰는 디스커버러 + 빈 위임 필터 + 기동 시 검증**

이 문서는 **설계 근거와 게이트웨이 내부 동작**을 다룬다. 왜 이런 구조인지, 무엇을 검토하고
기각했는지, 어떤 내부 동작에 기대고 있는지가 여기 있다.

필터를 추가하는 방법, 에러 처리, 헤더·바디 조작, 실행 방법 같은 **사용법은 [README](README.md)**에
있다. 중복을 피하기 위해 같은 내용을 두 번 적지 않는다.

검증은 전부 `spring-cloud-gateway-server-webmvc` **5.0.3** 소스 기준이다.

---

## 1. 목표

라우팅은 YAML로 관리한다. 각 라우트의 `filters`에는 `HandlerFilterFunction<ServerResponse,
ServerResponse>` 구현 빈을 **이름으로 나열**해서 적용한다. 리스트에 적은 순서가 곧 실행 순서다.

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
                - name: authFilter
                - name: tenantFilter
                - AddRequestHeader=X-Gateway,mvc-extensions
```

필터 구현체는 일반 Spring 빈으로 작성하고 생성자 주입을 자유롭게 쓸 수 있어야 한다. 오타와 누락은
첫 요청이 아니라 **기동 시점에** 드러나야 한다.

---

## 2. 배경 및 제약

**대상 버전.** Spring Cloud Gateway Server Web MVC 5.0.3 (Spring Cloud 2025.1.3). 패키지는
`org.springframework.cloud.gateway.server.mvc.*`, starter는
`spring-cloud-starter-gateway-server-webmvc`, 설정 prefix는 `spring.cloud.gateway.server.webmvc`다.

**빈 이름으로 바로 참조할 수 없는 이유.** YAML의 filter `name`은 `FilterSupplier`가 제공하는
**static 메서드 이름**에 매핑된다. `FilterSupplier`는 `Supplier<Collection<Method>>`일 뿐이라 빈
인스턴스를 이름으로 찾아주는 경로가 없다. `AbstractGatewayDiscoverer.addOperationMethod()`가
`method.getName()`을 키로 쓰고, `doDiscover()`가 `HandlerFilterFunction`을 반환하는 메서드만
필터로 인식한다.

**DI 제약.** static 메서드는 생성자 주입이 불가능하다. 그래서 요청 시점에
`MvcUtils.getApplicationContext(ServerRequest)`로 빈을 조회한다. 내장 필터 중 서킷브레이커,
로드밸런서, 토큰릴레이 계열도 같은 방식을 쓴다.

**Supplier 등록.** `FilterSupplier`를 `@Bean`으로 등록하면 된다.
`BeanFactoryGatewayDiscoverer.loadSuppliers()`가 `beanFactory.getBeanProvider(supplierClass)`로
해석하기 때문이다. 5.0.3 의 `META-INF/spring.factories`에는 `FilterSupplier` 항목이 **하나도 없고**
내장 필터도 전부 `@Bean` 공급자(`FilterAutoConfiguration`)다.

### 검토 후 기각한 대안

| 대안 | 기각 이유 |
| --- | --- |
| **방법 1** — 범용 위임 필터 하나만 등록(`- Bean=authFilter`) | 보일러플레이트는 가장 적지만 원하는 YAML 형태가 아니다. 다만 구현은 버리지 않고 방법 2의 내부 부품으로 재사용했다 — `BeanDelegatingFilter`가 바로 그것이다. `- Bean=` 표기는 **일부러 노출하지 않는다**(`BeanFilters.kt` 주석 참고): `- Bean=authFilter`와 `- name: authFilter`가 섞이면 라우트를 읽기 어려워진다 |
| **방법 3** — 자체 prefix로 라우트를 직접 바인딩해 `RouterFunction`을 조립 | 빈 이름 매핑은 자연스럽지만 `Path=...` predicate 와 내장 필터 축약 문법을 직접 파싱해야 해서 유지비가 크다 |
| **코드 생성** — 빈 이름당 static 메서드를 ByteBuddy/KSP로 생성 | 별칭을 아예 안 써도 되지만, 런타임 생성은 AOT/native 비친화를 들여오고 KSP는 별도 프로세서 모듈이 필요한 데다 Kotlin 2.4 를 아직 지원하지 않아 Kotlin 다운그레이드가 강제된다 |

---

## 3. 설계

스펙 최초안(방법 2)은 **필터당 별칭 메서드를 필터 이름으로** 두는 것이었다. 구현에서 한 단계 더
나아갔다: 별칭 메서드 이름을 인터페이스로 `alias()` 하나로 **고정**하고, YAML 이름은 **빈 이름에서**
가져온다. 그러려면 operations 맵의 키가 메서드명이 아니어야 하므로 디스커버러를 교체한다.

이 변경으로 얻은 것은 **컴파일러 강제**다. Kotlin/Java 는 static 멤버를 abstract 로 선언할 수 없어
어떤 상위 타입도 "이름이 X인 static 메서드가 있어야 한다"를 강제할 수 없다. 이름을 `alias()` 로
고정하면 `GatewayFilterAlias` 인터페이스가 그 존재와 이름과 반환 타입을 강제할 수 있다.

| 구성요소 | 역할 |
| --- | --- |
| `@GatewayFilterBean` | 필터 빈 표시. `@Component` 로 메타 어노테이션되어 있어 기본 컴포넌트 스캔이 수집하고, `AnnotationBeanNameGenerator` 가 `AuthFilter` → `authFilter` 로 이름을 만든다 — YAML 이름과 정확히 일치 |
| `GatewayFilterAlias` | companion object 가 구현하는 계약. `alias()` 의 존재·이름·반환 타입을 **컴파일러가 강제**한다 |
| `BeanFilters.kt` | `beanFilter<T>()`, `beanFilter(name)`, `factoryFilter<F>()`. `alias()` 본문에 쓴다 |
| `BeanDelegatingFilter` (internal) | 첫 요청에 `ApplicationContext` 에서 실제 빈을 찾아 캐시하고 위임한다. 공통 요청 트레이스도 여기서 남긴다 |
| `GatewayFilterBeanRegistry` | `@GatewayFilterBean` 빈과 각자의 static `alias()` 를 찾아 **빈 이름 → Method** 맵을 만든다. 문제는 모아서 한 번에 보고 |
| `AnnotatedFilterBeanDiscoverer` | 게이트웨이의 `FilterBeanFactoryDiscoverer` 를 `@Primary` 로 대체. `super.discover()` 후 위 맵을 **빈 이름 키로** operations 에 추가한다 |
| `BeanAliasFilterSupplier` | `SimpleFilterSupplier` 상속. 인자 받는 필터용 수동 별칭 클래스를 게이트웨이에 등록한다. 별칭 클래스를 **생성자로 받으므로** 프레임워크는 어떤 별칭이 있는지 모른다 |
| `GatewayFilterValidator` | `SmartInitializingSingleton`. 라우트가 0개여서 디스커버러가 호출되지 않는 경우까지 덮는다 |

`@GatewayFilterBean` 은 **무인자 필터**만 표현한다. 라우트마다 설정이 달라야 하는 필터는 단일 빈이
될 수 없으므로 팩토리 빈 + 수동 별칭(`GatewayArgsFilterAliases`)을 쓴다. 이쪽은 게이트웨이가 별칭을
메서드명으로 찾으므로 함수 이름이 곧 YAML 이름이다.

**검증기를 `ApplicationRunner` 가 아니라 `SmartInitializingSingleton` 으로 둔 이유.**
`ApplicationRunner` 는 `finishRefresh()` 이후, 즉 **웹서버가 이미 뜬 뒤**에 실행된다. 잘못된 설정이
한 요청이라도 받는 것을 막으려면 `finishBeanFactoryInitialization()` 단계여야 한다.

**경계.** 의존은 애플리케이션 → 프레임워크 한 방향이다. `framework/` 에서 `routing/`·`security/` 를
import 하는 곳은 없다. 프레임워크는 `ObjectProvider<BeanAliasFilterSupplier>` 로 **있으면 쓰고 없으면
넘어간다** — 수동 별칭을 안 쓰는 프로젝트는 그 설정을 두지 않으면 된다. 패키지로만 나뉘어 있어
컴파일러가 강제하지는 않지만, `framework/` 를 별도 Gradle 모듈로 떼는 작업이 파일 이동만으로 끝난다.

---

## 4. 게이트웨이 내부에 기대는 지점

이 구조가 성립하는 근거를 소스에서 확인한 것들이다. 게이트웨이를 올릴 때 여기가 깨지면 테스트가
잡아낸다.

| # | 기대하는 동작 | 근거 (5.0.3) | 가드 |
| --- | --- | --- | --- |
| 1 | 디스커버러를 `@Primary` 로 대체할 수 있다 | `FilterAutoConfiguration` 이 `filterBeanFactoryDiscoverer` 를 **`@ConditionalOnMissingBean` 없이** 선언 → 정의 오버라이딩 불가, 타입 기반 주입을 `@Primary` 로 이기는 수밖에 없다 | `DiscovererReplacementTests` |
| 2 | `FilterSupplier` 를 `@Bean` 으로 등록하면 수집된다 | `BeanFactoryGatewayDiscoverer.loadSuppliers()` → `beanFactory.getBeanProvider(supplierClass).orderedStream()` | 전체 통합 테스트 |
| 3 | operations 맵의 키는 메서드명이다 | `AbstractGatewayDiscoverer.addOperationMethod()` → `String key = method.getName()` | — (이걸 우회하려고 디스커버러를 교체한다) |
| 4 | YAML 이름 정규화는 `uncapitalize` 뿐이다 | `RouterFunctionHolderFactory.translate()` → `StringUtils.uncapitalize(operationName)`, 그 외 변환 없음 | `AliasNameMatchingTests` |
| 5 | 무인자 `- name: x` 가 무인자 static 메서드에 매칭된다 | `matchOperation()` → `parameters.getParameterCount() != normalizedArgs.size()` 면 탈락, 이후 파라미터 **이름** 포함 여부 확인 | 전체 통합 테스트 |
| 6 | 라우트는 기동 중 eager 하게 빌드된다 | `routerFunctionMapping` 이 refresh 중 `RouterFunction` 빈들을 모으면서 전부 빌드 → YAML 오타가 그 시점에 실패 | `StartupFailureTests` |
| 7 | 라우트 refresh 시 별칭이 재호출된다 | `RouterFunctionHolderFactory` 가 라우트를 다시 빌드하며 별칭 메서드를 호출 → 새 `BeanDelegatingFilter` 인스턴스, 캐시된 delegate 는 함께 버려짐 | — |

**단 한 곳의 내부 결합은 #1이다.** 나머지는 공개 동작이거나 우리가 우회하는 대상이다.

### 이름 충돌이 실제로 어떻게 동작하는가

흔한 오해를 하나 정리해 둔다. "bean 기반 operation 이 built-in 보다 먼저 맵에 들어가므로 충돌 시
조용히 섀도잉된다"는 **사실이 아니다.**

- `operations` 는 `MultiValueMap<String, OperationMethod>` 다. 이름이 겹치면 **둘 다** 같은 키에 남는다.
- `findOperation()` 은 `isConfigurable` 로 정렬한 뒤 `matchOperation()` 을 통과하는 **첫 항목**을 쓴다.
- `matchOperation()` 이 **인자 개수**로 거르므로, 무인자 별칭은 2-인자 built-in `addRequestHeader(name, values...)`
  를 가로채지 못한다. 반대로 `- name: addRequestHeader` (무인자)는 built-in 이 답할 수 없다.
- 즉 **인자 개수까지 같은** 충돌일 때만 실제로 가려지고, 그때 승자는 등록 순서다. 내장 필터 중
  `stripPrefix`, `preserveHost`, `preserveHostHeader`, `adaptCachedBody`, `fallbackHeaders`,
  `stripContextPath` 가 무인자이므로 `@GatewayFilterBean`(항상 무인자 별칭)과 충돌할 수 있다.

등록 순서에 의미가 생기는 것 자체가 문제이므로, 검사는 **따지지 않고 이름 재사용을 거부한다.**

### SimpleFilterSupplier 의 수집 범위 (주의)

`SimpleFilterSupplier.get()` 은 `filtersClass.getMethods()` — 즉 **모든 public 메서드**를 돌려주고,
걸러내는 쪽은 `doDiscover()` 의 반환 타입 검사다. 따라서 수동 별칭 클래스에 둔 public 헬퍼가
`HandlerFilterFunction` 을 반환하면 **그것도 YAML 에서 호출 가능한 필터로 등록된다.** 헬퍼는
`private` 로 두거나 반환 타입을 다르게 한다.

---

## 5. 구현 결과

클래스별 책임과 사용법은 [README의 프로젝트 구조](README.md#프로젝트-구조)와 각 파일의 KDoc 에 있다.
기동 시 검사 항목은 [README의 기동 시 검사](README.md#기동-시-검사)를 본다.

최초 스펙과 이름이 달라진 것들:

| 스펙 최초안 | 실제 |
| --- | --- |
| `BeanFilterFunctions` | `framework/filter/BeanFilters.kt` |
| `GatewayFilterAliases` | `routing/filter/GatewayArgsFilterAliases.kt` (인자 받는 필터 전용으로 축소) |
| `GatewayFilterAliasSupplier` | `framework/filter/registry/BeanAliasFilterSupplier.kt` (별칭 클래스를 생성자로 받음) |
| 검증 `ApplicationRunner` | `framework/filter/validation/GatewayFilterValidator.kt` (`SmartInitializingSingleton`) |
| `BeanFilterSupplier` (선택) | 도입하지 않음 — `- Bean=` 표기를 노출하지 않기로 결정 |
| 별칭 메서드 이름 = 필터 이름 | `alias()` 로 고정, YAML 이름은 빈 이름에서 |
| `REFERENCED` 필드로 참조 이름 수집 | `GatewayFilterBeanRegistry` 가 빈에서 직접 수집 (전역 가변 상태 없음) |

---

## 6. 테스트

29개가 돌고 있다. 무엇을 고정해 두었는지는 [README의 테스트 표](README.md#테스트)에 있다.

최초 스펙의 테스트 계획에서 달라진 점:

- **회귀 테스트** — "기존 라우트(Java DSL 포함)가 그대로 동작" 항목은 이 프로젝트가 greenfield 라
  해당 사항이 없었다. 대신 `BuiltInFilterRegressionTests` 가 "우리 `FilterSupplier` 가 built-in 을
  가리지 않는지"를 지킨다.
- **다운스트림 스텁** — WireMock 으로 정했다. 포트를 동적으로 잡아 병렬 실행이 안전하고,
  "호출되지 않았음"(401 단락)을 request journal 로 단정할 수 있다.
- **`modifyRequestBody` 위임** — 스펙에서 "가능할 것으로 예상(미검증)" 이었던 항목이다. 실제로
  동작하며 `ModifyRequestBodyDelegationTests` 가 증거다.

---

## 7. 구버전 참고 (검증하지 않음)

이 저장소는 5.0.3 이 핀되어 있어 아래는 확인할 수 없다. 옮겨 쓰기 전에 해당 버전에서 직접 확인할 것.

`FilterBeanFactoryDiscoverer` 와 `BeanFactoryGatewayDiscoverer` 가 없던 버전에서는
`AbstractGatewayDiscoverer.loadSuppliers()` 가 `SpringFactoriesLoader` 만 쓴다. 대체할 빈도 없고
`FilterSupplier` 를 `@Bean` 으로 등록해도 수집되지 않으므로 `@GatewayFilterBean` 방식을 쓸 수 없고,
수동 별칭 경로만 남으며 등록은 `META-INF/spring.factories` 로 한다. `SpringFactoriesLoader` 는
무인자 생성자를 요구하므로 서브클래스를 하나 둬야 한다.

```
org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier=\
  com.example.gateway.routing.filter.GatewayArgsFilterAliasSupplier
```

설정 prefix 가 `spring.cloud.gateway.mvc` 였던 버전도 있다.

---

## 8. 참고 문서

- Writing Custom Predicates and Filters (Server Web MVC): https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/writing-custom-predicates-and-filters.html
- ModifyRequestBody Filter (Server MVC): https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-mvc/filters/modifyrequestbody.html
- FilterSupplier javadoc (5.0.2): https://javadoc.io/static/org.springframework.cloud/spring-cloud-gateway-server-webmvc/5.0.2/org/springframework/cloud/gateway/server/mvc/filter/FilterSupplier.html
- MvcUtils javadoc (5.0.2): https://javadoc.io/static/org.springframework.cloud/spring-cloud-gateway-server-webmvc/5.0.2/org/springframework/cloud/gateway/server/mvc/common/MvcUtils.html
- 관련 이슈 #3250 (커스텀 필터를 spring.factories 없이 등록): https://github.com/spring-cloud/spring-cloud-gateway/issues/3250

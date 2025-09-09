# M-Inbody - 게임 데미지 미터기 애플리케이션


## ⚠️ 중요 고지사항 (IMPORTANT NOTICE)

**이 프로젝트는 오픈소스로 제공되며, 오직 학습 및 연구 목적으로만 사용되어야 합니다.**

**This project is provided as open source and should be used for LEARNING AND RESEARCH PURPOSES ONLY.**

### 면책 조항 (Disclaimer)
- 본 소프트웨어는 "있는 그대로(AS IS)" 제공됩니다
- 개발자/배포자는 이 소프트웨어의 사용으로 인한 어떠한 직접적, 간접적, 우발적, 특수적, 징벌적 또는 결과적 손해에 대해서도 책임을 지지 않습니다
- 사용자는 본인의 책임 하에 이 소프트웨어를 사용해야 합니다
- 게임 회사의 이용약관을 확인하고 준수할 책임은 전적으로 사용자에게 있습니다

## 📋 프로젝트 개요

M-Inbody는 Spring Boot와 Kotlin으로 개발된 실시간 게임 데미지 미터 애플리케이션입니다. 네트워크 패킷을 캡처하여 게임 내 전투 데이터를 분석하고 시각화합니다.

### 주요 기능
- 실시간 데미지 통계 및 분석
- 버프 상태 모니터링
- WebSocket을 통한 실시간 데이터 업데이트
- 웹 기반 UI 대시보드
- 네트워크 패킷 캡처 및 분석 (TCP Port 16000)

## 🛠️ 기술 스택

- **Backend Framework**: Spring Boot 3.3.4
- **Language**: Kotlin 1.9.22
- **Build Tool**: Gradle (Kotlin DSL)
- **Java Version**: JDK 17
- **패킷 캡처**: pcap4j (Npcap 필요)
- **주요 의존성**:
  - Spring WebSocket
  - Spring Cloud OpenFeign
  - OkHttp3
  - Kotlin Coroutines

## 📁 프로젝트 구조

```
m-inbody/
├── build.gradle.kts              # Gradle 빌드 설정
├── settings.gradle.kts           # 프로젝트 설정
├── gradlew / gradlew.bat        # Gradle Wrapper
├── deploy/                       # 배포 관련 스크립트
│   ├── build 스크립트           # Windows 빌드 배치 파일
│   ├── installer config         # Inno Setup 설치 프로그램 설정
│   └── launch4j config          # Java 실행 파일 래퍼 설정
└── src/
    └── main/
        └── kotlin/
            └── com/gamboo/minbody/
                ├── MinbodyApplication.kt    # 애플리케이션 진입점
                ├── client/                  # 외부 API 클라이언트
                ├── config/                  # Spring 설정
                │   ├── cache/              # 캐싱 설정
                │   ├── exception/          # 예외 처리
                │   ├── feign/              # Feign 클라이언트 설정
                │   ├── web/                # Web MVC 설정
                │   └── websocket/          # WebSocket 설정
                ├── constants/               # 상수 정의
                ├── model/                   # 도메인 모델
                ├── rest/                    # REST 컨트롤러
                └── service/                 # 비즈니스 로직
                    ├── buff/               # 버프 처리
                    ├── damage/             # 데미지 계산
                    ├── network/            # 네트워크 인터페이스
                    ├── packet/             # 패킷 캡처/파싱
                    └── skill/              # 스킬 매핑
```

## ⚡ 빠른 시작

https://lukeson.notion.site/M-2370a5ac02648026ad65dca0a649cd73?pvs=74

### 사전 요구사항

1. **Java 17 이상** 설치
2. **Windows 사용자**: Npcap 설치 (필수)
   - [Npcap 다운로드](https://npcap.com/#download)
   - 설치 시 "WinPcap API-compatible Mode" 옵션 선택
   - 관리자 권한으로 설치 필요

### 실행 방법

#### 방법 1: Gradle을 사용한 실행
```bash
# 프로젝트 디렉토리로 이동
cd m-inbody

# Windows
./gradlew.bat bootRun

# macOS/Linux
./gradlew bootRun
```

#### 방법 2: JAR 파일 빌드 후 실행
```bash
# JAR 파일 빌드
./gradlew build

# JAR 파일 실행 (관리자 권한 필요)
java -jar build/libs/m-inbody-0.0.1-SNAPSHOT.jar
```

### 접속 방법
애플리케이션 실행 시 자동으로 웹 브라우저가 열립니다.
- 기본 주소: `http://localhost:5000`
- 자동으로 열리지 않는 경우 수동으로 위 주소로 접속

## ⚙️ 설정

### 네트워크 인터페이스 설정
기본적으로 사용 가능한 네트워크 인터페이스를 자동으로 감지합니다.
특정 인터페이스를 지정하려면 환경 변수나 설정 파일을 통해 구성할 수 있습니다.

### 패킷 필터 설정
기본 패킷 필터: `tcp and src port 16000`

## 🔧 문제 해결

### Windows에서 "Npcap을 찾을 수 없음" 오류
1. Npcap이 설치되어 있는지 확인
2. 관리자 권한으로 애플리케이션 실행
3. Npcap 재설치 (WinPcap 호환 모드 선택)

### 패킷 캡처가 작동하지 않는 경우
1. 애플리케이션을 관리자/root 권한으로 실행
2. 바이러스 백신이나 방화벽이 차단하고 있지 않은지 확인
3. 올바른 네트워크 인터페이스가 선택되었는지 확인

### 포트 5000이 이미 사용 중인 경우
다른 포트로 변경:
```bash
java -jar -Dserver.port=8080 build/libs/m-inbody-0.0.1-SNAPSHOT.jar
```

## 📝 개발 환경 설정

### IntelliJ IDEA
1. 프로젝트를 Gradle 프로젝트로 Import
2. JDK 17 설정
3. Kotlin 플러그인 설치 (기본 포함)

### VS Code
1. Java Extension Pack 설치
2. Kotlin 언어 지원 확장 설치
3. Gradle for Java 확장 설치

## 🤝 기여 방법

이 프로젝트는 학습 목적으로 제공됩니다. 
- 버그 리포트와 개선 제안은 Issues를 통해 제출해주세요
- Pull Request는 학습 목적의 개선사항만 받습니다

## 📜 라이선스

이 프로젝트는 교육 및 연구 목적으로만 제공됩니다.
상업적 사용은 금지되며, 사용으로 인한 모든 책임은 사용자에게 있습니다.

## ⚠️ 경고

- 이 소프트웨어를 사용하기 전에 해당 게임의 이용약관을 확인하세요
- 일부 게임에서는 이러한 도구의 사용이 금지될 수 있습니다
- 사용자는 모든 관련 규정을 준수할 책임이 있습니다
- 개발자는 부적절한 사용으로 인한 결과에 대해 책임지지 않습니다

---

**Remember**: This software is provided for educational purposes only. The developers assume no liability for any consequences of using this software.

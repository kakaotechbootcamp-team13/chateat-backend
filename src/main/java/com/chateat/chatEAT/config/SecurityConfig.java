package com.chateat.chatEAT.config;

import com.chateat.chatEAT.auth.filter.JwtAuthenticationFilter;
import com.chateat.chatEAT.auth.filter.JwtVerificationFilter;
import com.chateat.chatEAT.auth.handler.CustomAccessDeniedHandler;
import com.chateat.chatEAT.auth.handler.CustomAuthenticationEntryPoint;
import com.chateat.chatEAT.auth.handler.LoginFailureHandler;
import com.chateat.chatEAT.auth.handler.LoginSuccessHandler;
import com.chateat.chatEAT.auth.jwt.JwtTokenProvider;
import com.chateat.chatEAT.auth.principaldetails.PrincipalDetailsService;
import com.chateat.chatEAT.domain.member.service.MemberService;
import com.chateat.chatEAT.global.redis.RedisService;
import com.chateat.chatEAT.oauth2.CustomOAuth2MemberService;
import com.chateat.chatEAT.oauth2.handler.OAuth2LoginFailureHandler;
import com.chateat.chatEAT.oauth2.handler.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.CharacterEncodingFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberService memberService;
    private final AES128Config aes128Config;
    private final RedisService redisService;
    private final PrincipalDetailsService principalDetailsService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final CustomOAuth2MemberService customOAuth2MemberService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .headers(headerConfig -> headerConfig.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))
                .with(new CustomFilterConfigurer(), Customizer.withDefaults())
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/", "/members/join", "/auth/login", "/auth/reissue",
                                "/members/email-check/**",
                                "members/nickname-check/**", "/swagger-ui/**",
                                "/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2Login((oauth2Login) ->
                        oauth2Login
                                .userInfoEndpoint(userInfoEndpointConfig -> userInfoEndpointConfig.userService(
                                        customOAuth2MemberService))
                                .successHandler(oAuth2LoginSuccessHandler)
                                .failureHandler(oAuth2LoginFailureHandler)
                )
                .logout(logout -> logout.deleteCookies("JSESSIONID")
                        .logoutUrl("/auth/logout"));

        return http.build();
    }

//    @Bean
//    CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://www.chateat.store"));
//        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
//        configuration.setAllowCredentials(true);
//        configuration.addExposedHeader("Authorization");
//        configuration.addExposedHeader("Refresh");
//        configuration.addAllowedHeader("*");
//        configuration.setMaxAge(3600L);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(
                AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(principalDetailsService)
                .passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    public class CustomFilterConfigurer extends AbstractHttpConfigurer<CustomFilterConfigurer, HttpSecurity> {
        @Override
        public void configure(HttpSecurity builder) {
            CharacterEncodingFilter filter = new CharacterEncodingFilter();
            filter.setEncoding("UTF-8");
            filter.setForceEncoding(true);
            builder.addFilterBefore(filter, JwtAuthenticationFilter.class);
            log.info("CustomFilterConfigurer configured");
            AuthenticationManager authenticationManager = builder.getSharedObject(AuthenticationManager.class);
            JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(authenticationManager,
                    jwtTokenProvider, aes128Config, memberService, redisService);
            JwtVerificationFilter jwtVerificationFilter = new JwtVerificationFilter(jwtTokenProvider, redisService);

            jwtAuthenticationFilter.setFilterProcessesUrl("/auth/login");
            jwtAuthenticationFilter.setAuthenticationSuccessHandler(new LoginSuccessHandler());
            jwtAuthenticationFilter.setAuthenticationFailureHandler(new LoginFailureHandler());

            builder
                    .addFilter(jwtAuthenticationFilter)
                    .addFilterAfter(jwtVerificationFilter, JwtAuthenticationFilter.class);
        }
    }
}

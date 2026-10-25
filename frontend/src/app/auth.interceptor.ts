import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from './auth.service';
import { API_BASE_URL } from './api-url';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.getAccessToken();
  if (!request.url.startsWith(`${API_BASE_URL}/`)) {
    return next(request);
  }
  const router = inject(Router);
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      const isAuthRequest =
        request.url.endsWith('/auth/login') || request.url.endsWith('/auth/signup');
      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthRequest) {
        auth.logout();
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    }),
  );
};

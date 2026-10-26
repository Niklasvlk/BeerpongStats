import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './api/auth-api';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // XSRF-TOKEN-Cookie -> X-XSRF-TOKEN-Header übernimmt Angular selbst
    provideHttpClient(withFetch()),
    provideAppInitializer(() => firstValueFrom(inject(AuthApi).restoreSession())),
  ],
};
